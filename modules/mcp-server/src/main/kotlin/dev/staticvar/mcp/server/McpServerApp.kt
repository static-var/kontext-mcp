package dev.staticvar.mcp.server

import dev.staticvar.mcp.server.search.SearchService
import dev.staticvar.mcp.shared.model.SearchRequest
import dev.staticvar.mcp.shared.model.SearchResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import kotlin.system.exitProcess

private const val SERVER_NAME = "android-kotlin-mcp"
private const val SERVER_VERSION = "0.1.0"

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>) = runBlocking {
    val options = try {
        parseArgs(args)
    } catch (exception: IllegalArgumentException) {
        logger.error(exception) { "Invalid command line arguments" }
        printUsage()
        return@runBlocking
    }

    val components = try {
        McpServerBootstrap.initialize(options.configPath)
    } catch (exception: Exception) {
        logger.error(exception) { "Failed to initialize MCP server components" }
        return@runBlocking
    }

    try {
        runServer(components)
    } catch (cancellation: CancellationException) {
        logger.info { "MCP server cancelled: ${cancellation.message}" }
        throw cancellation
    } catch (unexpected: Exception) {
        logger.error(unexpected) { "Unhandled MCP server failure" }
    } finally {
        components.close()
    }
}

private suspend fun runServer(components: McpServerComponents) {
    val server = buildServer(components.searchService)
    val input = System.`in`.asSource().buffered()
    val output = System.out.asSink().buffered()
    val transport = StdioServerTransport(input, output)

    val session = try {
        server.createSession(transport)
    } catch (exception: Exception) {
        logger.error(exception) { "Failed to establish MCP session" }
        try {
            transport.close()
        } catch (closeError: Exception) {
            logger.debug(closeError) { "Transport close after failed session establishment also failed." }
        }
        return
    }

    val runtimeJob = currentCoroutineContext().job
    session.onClose {
        logger.info { "MCP session closed; shutting down." }
        runtimeJob.cancel(CancellationException("Session closed"))
    }

    val shutdownHook = Thread {
        runBlocking {
            logger.info { "Shutdown hook triggered, closing MCP server." }
            runCatching { session.close() }.onFailure { logger.warn(it) { "Failed to close session gracefully" } }
            runCatching { server.close() }.onFailure { logger.warn(it) { "Failed to close server gracefully" } }
        }
    }
    Runtime.getRuntime().addShutdownHook(shutdownHook)

    try {
        awaitCancellation()
    } finally {
        runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }.onFailure { /* JVM is shutting down */ }
        runCatching { session.close() }.onFailure { logger.debug(it) { "Session close during shutdown" } }
        runCatching { server.close() }.onFailure { logger.debug(it) { "Server close during shutdown" } }
    }
}

internal fun buildServer(searchService: SearchService): Server {
    val capabilities = ServerCapabilities(
        tools = ServerCapabilities.Tools(listChanged = true)
    )

    return Server(
        serverInfo = Implementation(name = SERVER_NAME, version = SERVER_VERSION),
        options = ServerOptions(capabilities = capabilities)
    ) {
        registerSearchTool(this, searchService)
    }
}

private fun registerSearchTool(server: Server, searchService: SearchService) {
    server.addTool(
        name = "search_docs",
        title = "Search Documentation",
        description = "Retrieve relevant Android and Kotlin documentation chunks for the provided query.",
        inputSchema = SEARCH_INPUT_SCHEMA
    ) { request ->
        handleSearchDocs(request, searchService)
    }
}

private suspend fun handleSearchDocs(
    request: CallToolRequest,
    searchService: SearchService
): CallToolResult {
    val searchRequest = try {
        McpJson.decodeFromJsonElement(SearchRequest.serializer(), request.arguments)
    } catch (exception: SerializationException) {
        logger.warn(exception) { "Invalid search_docs arguments: ${request.arguments}" }
        return errorResult("Invalid arguments for search_docs tool: ${exception.localizedMessage ?: "bad input"}")
    }

    return runCatching {
        val response = searchService.search(searchRequest)
        successResult(response)
    }.getOrElse { throwable ->
        logger.error(throwable) { "search_docs tool execution failed" }
        errorResult(userFacingMessage(throwable))
    }
}

private fun successResult(response: SearchResponse): CallToolResult {
    val textContent = TextContent(formatSearchResponse(response))
    val structured = McpJson.encodeToJsonElement(SearchResponse.serializer(), response).jsonObject

    return CallToolResult(
        content = listOf(textContent),
        structuredContent = structured
    )
}

private fun errorResult(message: String): CallToolResult =
    CallToolResult(
        content = listOf(TextContent(message)),
        structuredContent = buildJsonObject { put("error", JsonPrimitive(message)) },
        isError = true
    )

private fun userFacingMessage(throwable: Throwable): String = when (throwable) {
    is IllegalArgumentException -> throwable.localizedMessage ?: "Invalid request"
    is IllegalStateException -> throwable.localizedMessage ?: "Request could not be processed"
    else -> "search_docs failed: ${throwable.localizedMessage ?: throwable::class.simpleName}"
}

private fun formatSearchResponse(response: SearchResponse): String {
    if (response.chunks.isEmpty()) {
        return "No documentation chunks matched your query."
    }

    val builder = StringBuilder()
    builder.appendLine(
        "Found ${response.chunks.size} chunk(s). " +
            "Tokens=${response.totalTokens}, confidence=${formatSimilarity(response.confidence)}."
    )

    response.chunks.forEachIndexed { index, chunk ->
        builder.appendLine()
        builder.appendLine("${index + 1}. ${chunk.source} (score=${formatSimilarity(chunk.similarity)})")
        builder.appendLine(chunk.content.trim())
        if (chunk.metadata.isNotEmpty()) {
            builder.appendLine("metadata: ${chunk.metadata}")
        }
    }

    return builder.toString()
}

private fun formatSimilarity(value: Float): String =
    String.format(Locale.US, "%.2f", value)

private data class CliOptions(val configPath: Path?)

private fun parseArgs(args: Array<String>): CliOptions {
    var configPath: Path? = null
    var index = 0

    while (index < args.size) {
        when (val arg = args[index]) {
            "--config" -> {
                val value = args.getOrNull(index + 1)
                    ?: throw IllegalArgumentException("--config requires a path argument")
                configPath = Paths.get(value)
                index += 2
            }

            "--help", "-h" -> {
                printUsage()
                exitProcess(0)
            }

            else -> throw IllegalArgumentException("Unknown argument: $arg")
        }
    }

    return CliOptions(configPath)
}

private fun printUsage() {
    println(
        """
        Usage: mcp-server [--config <path>]

        Options:
          --config <path>   Override the default configuration file location.
          --help            Show this message.
        """.trimIndent()
    )
}

private val SEARCH_INPUT_SCHEMA = Tool.Input(
    properties = buildJsonObject {
        put(
            "query",
            buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("description", JsonPrimitive("Natural language search query."))
            }
        )
        put(
            "tokenBudget",
            buildJsonObject {
                put("type", JsonPrimitive("integer"))
                put("minimum", JsonPrimitive(1))
                put("description", JsonPrimitive("Maximum number of tokens allowed in the response."))
            }
        )
        put(
            "filters",
            buildJsonObject {
                put("type", JsonPrimitive("object"))
                put("description", JsonPrimitive("Optional metadata filters (e.g., {\"api_level\": \"34\"})."))
                put(
                    "additionalProperties",
                    buildJsonObject { put("type", JsonPrimitive("string")) }
                )
            }
        )
        put(
            "similarityThreshold",
            buildJsonObject {
                put("type", JsonPrimitive("number"))
                put("minimum", JsonPrimitive(0))
                put("maximum", JsonPrimitive(1))
                put("description", JsonPrimitive("Override for minimum chunk similarity (0.0-1.0)."))
            }
        )
    },
    required = listOf("query")
)
