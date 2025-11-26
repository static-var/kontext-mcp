package dev.staticvar.mcp.crawler.server.bootstrap

import dev.staticvar.mcp.crawler.server.service.CrawlerServices
import dev.staticvar.mcp.crawler.server.service.SystemStatsServiceImpl
import dev.staticvar.mcp.crawler.server.service.impl.CoordinatedCrawlScheduleService
import dev.staticvar.mcp.crawler.server.service.impl.CrawlSchedulerCoordinator
import dev.staticvar.mcp.crawler.server.service.impl.CrawlerOrchestrator
import dev.staticvar.mcp.crawler.server.service.impl.DatabaseCrawlScheduleService
import dev.staticvar.mcp.crawler.server.service.impl.DatabaseSourceUrlService
import dev.staticvar.mcp.crawler.server.service.impl.WebSearchService
import dev.staticvar.mcp.embedder.service.EmbeddingService
import dev.staticvar.mcp.embedder.service.EmbeddingServiceFactory
import dev.staticvar.mcp.embedder.service.OnnxCrossEncoderReranker
import dev.staticvar.mcp.embedder.service.RerankerService
import dev.staticvar.mcp.embedder.util.ModelDownloader
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
        val httpClient = createHttpClient(appConfig)
        val embeddingService = EmbeddingServiceFactory.createBgeService(appConfig.embedding, httpClient)
        val modelDownloader = ModelDownloader(httpClient)
        val rerankerService = OnnxCrossEncoderReranker(appConfig.reranking, modelDownloader)
        rerankerService.init()

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
        val statsService = SystemStatsServiceImpl(appConfig, documentRepository, embeddingRepository, sourceUrlRepository)
        val searchService =
            WebSearchService(
                embeddingService = embeddingService,
                embeddingRepository = embeddingRepository,
                rerankerService = rerankerService,
                retrievalConfig = appConfig.retrieval,
                rerankingConfig = appConfig.reranking,
            )

        val services =
            CrawlerServices(
                status = orchestrator,
                scheduler = scheduleService,
                executor = orchestrator,
                sources = sourceService,
                stats = statsService,
                search = searchService,
            )

        coordinator.reload()

        return CrawlerComponents(
            appConfig = appConfig,
            services = services,
            orchestrator = orchestrator,
            coordinator = coordinator,
            embeddingService = embeddingService,
            rerankerService = rerankerService,
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
    private val rerankerService: RerankerService,
    private val httpClient: HttpClient,
) : Closeable {
    override fun close() {
        runCatching { coordinator.close() }
        runCatching { orchestrator.close() }
        runCatching { embeddingService.close() }
        runCatching { rerankerService.close() }
        runCatching { httpClient.close() }
    }
}
