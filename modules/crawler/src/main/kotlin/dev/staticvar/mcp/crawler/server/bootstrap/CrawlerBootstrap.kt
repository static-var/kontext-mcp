package dev.staticvar.mcp.crawler.server.bootstrap

import dev.staticvar.mcp.crawler.server.service.CrawlerServices
import dev.staticvar.mcp.crawler.server.service.impl.CoordinatedCrawlScheduleService
import dev.staticvar.mcp.crawler.server.service.impl.CrawlSchedulerCoordinator
import dev.staticvar.mcp.crawler.server.service.impl.CrawlerOrchestrator
import dev.staticvar.mcp.crawler.server.service.impl.DatabaseCrawlScheduleService
import dev.staticvar.mcp.crawler.server.service.impl.DatabaseSourceUrlService
import dev.staticvar.mcp.embedder.service.EmbeddingService
import dev.staticvar.mcp.embedder.service.EmbeddingServiceFactory
import dev.staticvar.mcp.indexer.database.DatabaseFactory
import dev.staticvar.mcp.indexer.repository.impl.DocumentRepositoryImpl
import dev.staticvar.mcp.indexer.repository.impl.EmbeddingRepositoryImpl
import dev.staticvar.mcp.indexer.repository.impl.SourceUrlRepositoryImpl
import dev.staticvar.mcp.parser.ParserModule
import dev.staticvar.mcp.shared.config.AppConfig
import dev.staticvar.mcp.shared.config.AppConfigLoader
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import java.io.Closeable
import java.nio.file.Path

object CrawlerBootstrap {
    suspend fun initialize(configPath: Path? = null): CrawlerComponents {
        val appConfig = AppConfigLoader.load(configPath, allowMissing = configPath == null)

        DatabaseFactory.init(appConfig.database)

        val parserRegistry = ParserModule.defaultRegistry()
        val embeddingService = EmbeddingServiceFactory.createBgeService(appConfig.embedding)
        val httpClient = createHttpClient(appConfig)

        val sourceUrlRepository = SourceUrlRepositoryImpl()
        val documentRepository = DocumentRepositoryImpl()
        val embeddingRepository = EmbeddingRepositoryImpl()

        val orchestrator =
            CrawlerOrchestrator(
                sourceUrlRepository = sourceUrlRepository,
                documentRepository = documentRepository,
                embeddingRepository = embeddingRepository,
                parserRegistry = parserRegistry,
                embeddingService = embeddingService,
                chunkingConfig = appConfig.chunking,
                crawlerConfig = appConfig.crawler,
                httpClient = httpClient,
            )

        val rawScheduleService = DatabaseCrawlScheduleService()
        val coordinator = CrawlSchedulerCoordinator(rawScheduleService, orchestrator)
        val scheduleService = CoordinatedCrawlScheduleService(rawScheduleService, coordinator)
        val sourceService = DatabaseSourceUrlService(sourceUrlRepository, parserRegistry)

        val services =
            CrawlerServices(
                status = orchestrator,
                scheduler = scheduleService,
                executor = orchestrator,
                sources = sourceService,
            )

        coordinator.reload()

        return CrawlerComponents(
            appConfig = appConfig,
            services = services,
            orchestrator = orchestrator,
            coordinator = coordinator,
            embeddingService = embeddingService,
            httpClient = httpClient,
        )
    }

    private fun createHttpClient(config: AppConfig): HttpClient =
        HttpClient(CIO) {
            install(HttpTimeout) {
                val timeoutMillis = config.crawler.timeout
                requestTimeoutMillis = timeoutMillis
                connectTimeoutMillis = timeoutMillis
                socketTimeoutMillis = timeoutMillis
            }
        }
}

class CrawlerComponents(
    val appConfig: AppConfig,
    val services: CrawlerServices,
    private val orchestrator: CrawlerOrchestrator,
    private val coordinator: CrawlSchedulerCoordinator,
    private val embeddingService: EmbeddingService,
    private val httpClient: HttpClient,
) : Closeable {
    override fun close() {
        runCatching { coordinator.close() }
        runCatching { orchestrator.close() }
        runCatching { embeddingService.close() }
        runCatching { httpClient.close() }
    }
}
