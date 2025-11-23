package dev.staticvar.mcp.server

import dev.staticvar.mcp.embedder.service.EmbeddingService
import dev.staticvar.mcp.embedder.service.EmbeddingServiceFactory
import dev.staticvar.mcp.embedder.service.OnnxCrossEncoderReranker
import dev.staticvar.mcp.embedder.service.RerankerService
import dev.staticvar.mcp.embedder.util.ModelDownloader
import dev.staticvar.mcp.indexer.database.DatabaseFactory
import dev.staticvar.mcp.indexer.repository.EmbeddingRepository
import dev.staticvar.mcp.indexer.repository.impl.EmbeddingRepositoryImpl
import dev.staticvar.mcp.server.search.SearchService
import dev.staticvar.mcp.shared.config.AppConfig
import dev.staticvar.mcp.shared.config.AppConfigLoader
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import java.nio.file.Path

/**
 * Builds the runtime wiring for the MCP server: configuration, persistence, and services.
 */
object McpServerBootstrap {

    /**
     * Initialize core dependencies needed by the MCP server.
     *
     * @param configPath Optional configuration file override.
     */
    suspend fun initialize(configPath: Path? = null): McpServerComponents {
        val config = AppConfigLoader.load(configPath, allowMissing = configPath == null)

        DatabaseFactory.init(config.database)

        val httpClient = HttpClient(CIO)
        val modelDownloader = ModelDownloader(httpClient)

        val embeddingService = EmbeddingServiceFactory.createBgeService(config.embedding, httpClient)
        
        val rerankerService = OnnxCrossEncoderReranker(config.reranking, modelDownloader)
        rerankerService.init()

        val embeddingRepository: EmbeddingRepository = EmbeddingRepositoryImpl()
        val searchService = SearchService(
            embeddingService = embeddingService,
            embeddingRepository = embeddingRepository,
            rerankerService = rerankerService,
            retrievalConfig = config.retrieval,
            rerankingConfig = config.reranking
        )

        return McpServerComponents(
            config = config,
            searchService = searchService,
            embeddingService = embeddingService,
            rerankerService = rerankerService,
            httpClient = httpClient
        )
    }
}

/**
 * Simple container for MCP server dependencies with lifecycle management.
 */
class McpServerComponents(
    val config: AppConfig,
    val searchService: SearchService,
    private val embeddingService: EmbeddingService,
    private val rerankerService: RerankerService,
    private val httpClient: HttpClient
) : AutoCloseable {

    override fun close() {
        runCatching { embeddingService.close() }
        runCatching { rerankerService.close() }
        runCatching { httpClient.close() }
    }
}
