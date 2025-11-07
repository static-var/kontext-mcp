package dev.staticvar.mcp.server

import dev.staticvar.mcp.embedder.service.EmbeddingService
import dev.staticvar.mcp.embedder.service.EmbeddingServiceFactory
import dev.staticvar.mcp.indexer.database.DatabaseFactory
import dev.staticvar.mcp.indexer.repository.EmbeddingRepository
import dev.staticvar.mcp.indexer.repository.impl.EmbeddingRepositoryImpl
import dev.staticvar.mcp.server.search.SearchService
import dev.staticvar.mcp.shared.config.AppConfig
import dev.staticvar.mcp.shared.config.AppConfigLoader
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

        val embeddingService = EmbeddingServiceFactory.createBgeService(config.embedding)
        val embeddingRepository: EmbeddingRepository = EmbeddingRepositoryImpl()
        val searchService = SearchService(embeddingService, embeddingRepository, config.retrieval)

        return McpServerComponents(
            config = config,
            searchService = searchService,
            embeddingService = embeddingService
        )
    }
}

/**
 * Simple container for MCP server dependencies with lifecycle management.
 */
class McpServerComponents(
    val config: AppConfig,
    val searchService: SearchService,
    private val embeddingService: EmbeddingService
) : AutoCloseable {

    override fun close() {
        runCatching { embeddingService.close() }
    }
}
