@file:OptIn(kotlin.time.ExperimentalTime::class)

package dev.staticvar.mcp.crawler.server.service.impl

import dev.staticvar.mcp.crawler.server.service.CrawlExecutionService
import dev.staticvar.mcp.crawler.server.service.CrawlStatusService
import dev.staticvar.mcp.crawler.server.service.CrawlStatusSnapshot
import dev.staticvar.mcp.crawler.server.service.CrawlTriggerResult
import dev.staticvar.mcp.embedder.service.EmbeddingService
import dev.staticvar.mcp.embedder.service.model.EmbeddingBatchRequest
import dev.staticvar.mcp.indexer.repository.DocumentRepository
import dev.staticvar.mcp.indexer.repository.EmbeddingRepository
import dev.staticvar.mcp.indexer.repository.SourceUrlRepository
import dev.staticvar.mcp.parser.api.ParseRequest
import dev.staticvar.mcp.parser.api.ParserContext
import dev.staticvar.mcp.parser.registry.ParserRegistry
import dev.staticvar.mcp.shared.config.ChunkingConfig
import dev.staticvar.mcp.shared.config.CrawlerConfig
import dev.staticvar.mcp.shared.model.CrawlStatus
import dev.staticvar.mcp.shared.model.DocumentChunk
import dev.staticvar.mcp.shared.model.EmbeddedChunk
import dev.staticvar.mcp.shared.model.SourceUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import java.io.Closeable
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class CrawlerOrchestrator(
    private val sourceUrlRepository: SourceUrlRepository,
    private val documentRepository: DocumentRepository,
    private val embeddingRepository: EmbeddingRepository,
    private val parserRegistry: ParserRegistry,
    private val embeddingService: EmbeddingService,
    private val chunkingConfig: ChunkingConfig,
    private val crawlerConfig: CrawlerConfig,
    private val httpClient: HttpClient,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : CrawlExecutionService, CrawlStatusService, Closeable {

    private val state = MutableStateFlow<CrawlStatusSnapshot>(CrawlStatusSnapshot.Idle)
    private val jobGuard = Mutex()
    private var activeJobId: String? = null

    override suspend fun trigger(requestedBy: String): CrawlTriggerResult = jobGuard.withLock {
        if (!scope.coroutineContext.isActive) {
            return@withLock CrawlTriggerResult(
                accepted = false,
                jobId = null,
                message = "Crawler service is shutting down"
            )
        }

        val existingId = activeJobId
        if (existingId != null) {
            return@withLock CrawlTriggerResult(
                accepted = false,
                jobId = existingId,
                message = "Crawl job $existingId already running"
            )
        }

        val jobId = createJobId()
        activeJobId = jobId

        scope.launch {
            try {
                executeCrawl(jobId, requestedBy)
            } catch (unexpected: Exception) {
                logger.error(unexpected) { "Crawl job $jobId failed" }
                state.value = CrawlStatusSnapshot.Completed(
                    lastJobId = jobId,
                    completedAt = currentDateTime(),
                    processedCount = 0,
                    failureCount = 1,
                    notes = unexpected.message ?: "unknown failure"
                )
            } finally {
                jobGuard.withLock {
                    activeJobId = null
                    if (state.value is CrawlStatusSnapshot.Running) {
                        state.value = CrawlStatusSnapshot.Completed(
                            lastJobId = jobId,
                            completedAt = currentDateTime(),
                            processedCount = 0,
                            failureCount = 0,
                            notes = "Job terminated"
                        )
                    }
                }
            }
        }

        return@withLock CrawlTriggerResult(
            accepted = true,
            jobId = jobId,
            message = "Crawl triggered by $requestedBy"
        )
    }

    override suspend fun snapshot(): CrawlStatusSnapshot = state.value

    override fun close() {
        scope.cancel()
    }

    private suspend fun executeCrawl(jobId: String, requestedBy: String) {
        val startedAt = currentDateTime()
        state.value = CrawlStatusSnapshot.Running(
            activeJobId = jobId,
            processedCount = 0,
            totalCount = null,
            startedAt = startedAt,
            message = "Preparing crawl requested by $requestedBy"
        )

        val pending = sourceUrlRepository.findPending()
        val totalCount = pending.size
        state.update {
            CrawlStatusSnapshot.Running(
                activeJobId = jobId,
                processedCount = 0,
                totalCount = totalCount,
                startedAt = startedAt,
                message = if (totalCount == 0) "No pending URLs" else "Discovered $totalCount URL(s)"
            )
        }

        if (pending.isEmpty()) {
            state.value = CrawlStatusSnapshot.Completed(
                lastJobId = jobId,
                completedAt = currentDateTime(),
                processedCount = 0,
                failureCount = 0,
                notes = "No pending URLs"
            )
            return
        }

        var processed = 0
        var failures = 0

        for ((index, source) in pending.withIndex()) {
            state.update {
                CrawlStatusSnapshot.Running(
                    activeJobId = jobId,
                    processedCount = processed,
                    totalCount = totalCount,
                    startedAt = startedAt,
                    message = "Crawling ${source.url}"
                )
            }

            val outcome = runCatching { processSource(source) }
                .onFailure { throwable ->
                    failures += 1
                    logger.error(throwable) { "Failed to crawl ${source.url}" }
                    updateFailedStatus(source, throwable)
                }
                .getOrNull()

            if (outcome?.wasSuccessful == true) {
                processed += 1
            }

            state.update {
                CrawlStatusSnapshot.Running(
                    activeJobId = jobId,
                    processedCount = processed,
                    totalCount = totalCount,
                    startedAt = startedAt,
                    message = "Processed ${index + 1} of $totalCount"
                )
            }

            if (crawlerConfig.requestDelayMs > 0) {
                delay(crawlerConfig.requestDelayMs.milliseconds)
            }
        }

        state.value = CrawlStatusSnapshot.Completed(
            lastJobId = jobId,
            completedAt = currentDateTime(),
            processedCount = processed,
            failureCount = failures,
            notes = if (failures == 0) "Completed successfully" else "$failures source(s) failed"
        )
    }

    private suspend fun processSource(source: SourceUrl): ProcessOutcome {
        sourceUrlRepository.updateStatus(source.id, CrawlStatus.IN_PROGRESS, null)

        return runWithRetries("crawl ${source.url}") {
            processSourceAttempt(source)
        }
    }

    private suspend fun processSourceAttempt(source: SourceUrl): ProcessOutcome {
        val fetchOutcome = fetchSource(source)
        return when (fetchOutcome) {
            FetchOutcome.NotModified -> {
                sourceUrlRepository.markCrawled(source.id)
                sourceUrlRepository.updateStatus(source.id, CrawlStatus.SUCCESS, null)
                ProcessOutcome(success = true)
            }

            is FetchOutcome.Fetched -> {
                ingestContent(source, fetchOutcome)
                ProcessOutcome(success = true)
            }
        }
    }

    private suspend fun fetchSource(source: SourceUrl): FetchOutcome {
        val response = httpClient.get(source.url) {
            header(HttpHeaders.UserAgent, crawlerConfig.userAgent)
            source.etag?.let { header(HttpHeaders.IfNoneMatch, it) }
            source.lastModified?.let { header(HttpHeaders.IfModifiedSince, it) }
            timeout {
                requestTimeoutMillis = crawlerConfig.timeout
            }
        }

        return when {
            response.status == HttpStatusCode.NotModified -> FetchOutcome.NotModified
            response.status == HttpStatusCode.OK -> {
                val channel = response.bodyAsChannel()
                val packet = channel.readRemaining()
                val bytes = packet.readBytes()
                FetchOutcome.Fetched(
                    content = bytes,
                    mediaType = response.headers[HttpHeaders.ContentType],
                    etag = response.headers[HttpHeaders.ETag],
                    lastModified = response.headers[HttpHeaders.LastModified]
                )
            }
            response.status == HttpStatusCode.TooManyRequests -> {
                throw IllegalStateException("Rate limited while fetching ${source.url}")
            }
            response.status.value in 500..599 -> {
                throw IllegalStateException("Upstream error ${response.status.value} for ${source.url}")
            }
            response.status.value in 400..499 -> {
                throw NonRetryableCrawlException("HTTP ${response.status.value} for ${source.url}")
            }
            else -> throw IllegalStateException("Unexpected response ${response.status.value} for ${source.url}")
        }
    }

    private suspend fun ingestContent(source: SourceUrl, fetched: FetchOutcome.Fetched) {
        val parser = parserRegistry.forType(source.parserType)
            ?: parserRegistry.forUrl(source.url)
            ?: error("No parser available for ${source.url}")

        val parseRequest = ParseRequest(
            url = source.url,
            rawBytes = fetched.content,
            mediaType = fetched.mediaType,
            fetchedAt = currentTimeInstant()
        )

        val parseResult = parser.parse(parseRequest, ParserContext(chunking = chunkingConfig))

        // Generate embeddings outside the transaction to avoid holding the lock too long
        val chunks = parseResult.chunks
        val embeddedChunks = if (chunks.isNotEmpty()) {
            val embeddings = embeddingService.embed(EmbeddingBatchRequest(chunks.map(DocumentChunk::content)))
            embeddings.mapIndexed { index, embeddingResult ->
                EmbeddedChunk(
                    id = 0,
                    documentId = 0, // Will be updated after document insertion
                    chunkIndex = index,
                    content = chunks[index].content,
                    embedding = embeddingResult.embedding,
                    metadata = chunks[index].metadata,
                    tokenCount = embeddingResult.tokenCount
                )
            }
        } else {
            emptyList()
        }

        documentRepository.deleteBySourceUrl(source.url)
        val documentId = documentRepository.insert(parseResult.document)

        if (embeddedChunks.isNotEmpty()) {
            val chunksWithId = embeddedChunks.map { it.copy(documentId = documentId) }
            embeddingRepository.insertBatch(chunksWithId)
        }

        sourceUrlRepository.updateCrawlMetadata(source.id, fetched.etag, fetched.lastModified)
        sourceUrlRepository.markCrawled(source.id)
        sourceUrlRepository.updateStatus(source.id, CrawlStatus.SUCCESS, null)
    }

    private suspend fun updateFailedStatus(source: SourceUrl, throwable: Throwable) {
        val baseMessage = throwable.message?.take(MAX_ERROR_LENGTH) ?: throwable::class.simpleName.orEmpty()
        val attemptSuffix = if (crawlerConfig.retryAttempts > 1) {
            " (after ${crawlerConfig.retryAttempts} attempts)"
        } else {
            ""
        }
        val combined = (baseMessage + attemptSuffix).take(MAX_ERROR_LENGTH)
        sourceUrlRepository.updateStatus(source.id, CrawlStatus.FAILED, combined)
    }

    private suspend fun <T> runWithRetries(operationName: String, block: suspend () -> T): T {
        val maxAttempts = crawlerConfig.retryAttempts.coerceAtLeast(1)
        var attempt = 0
        var nextDelay = initialRetryDelay()
        var lastError: Throwable? = null

        while (attempt < maxAttempts) {
            try {
                return block()
            } catch (nonRetryable: NonRetryableCrawlException) {
                throw nonRetryable
            } catch (ex: Throwable) {
                lastError = ex
                attempt += 1
                if (attempt >= maxAttempts) {
                    throw ex
                }

                val waitFor = nextDelay.coerceAtMost(MAX_RETRY_DELAY)
                logger.warn(ex) {
                    "Attempt $attempt/$maxAttempts failed for $operationName. Retrying in ${waitFor.inWholeMilliseconds}ms"
                }
                delay(waitFor)
                nextDelay = (nextDelay * 2).coerceAtMost(MAX_RETRY_DELAY)
            }
        }

        throw lastError ?: IllegalStateException("Unexpected failure while $operationName")
    }

    private fun initialRetryDelay(): Duration {
        val configuredDelay = if (crawlerConfig.requestDelayMs > 0) {
            crawlerConfig.requestDelayMs.milliseconds
        } else {
            Duration.ZERO
        }
        return configuredDelay.coerceAtLeast(MIN_RETRY_DELAY)
    }

    private fun createJobId(): String = "crawl-${UUID.randomUUID()}"

    private fun currentDateTime(): Instant = Clock.System.now()

    private fun currentTimeInstant(): Instant = Instant.fromEpochMilliseconds(System.currentTimeMillis())

    private data class ProcessOutcome(val success: Boolean) {
        val wasSuccessful: Boolean get() = success
    }

    private sealed interface FetchOutcome {
        data object NotModified : FetchOutcome
        data class Fetched(
            val content: ByteArray,
            val mediaType: String?,
            val etag: String?,
            val lastModified: String?
        ) : FetchOutcome
    }

    private class NonRetryableCrawlException(message: String) : RuntimeException(message)

    private companion object {
        private const val MAX_ERROR_LENGTH = 512
        private val MIN_RETRY_DELAY = 250.milliseconds
        private val MAX_RETRY_DELAY = 5.seconds
        private val logger = KotlinLogging.logger {}
    }
}
