package dev.staticvar.mcp.crawler.server.service

import dev.staticvar.mcp.shared.model.CrawlStatus
import dev.staticvar.mcp.shared.model.ParserType
import kotlin.time.Instant

/**
 * Aggregates service dependencies used by the crawler web server.
 */
data class CrawlerServices(
    val status: CrawlStatusService,
    val scheduler: CrawlScheduleService,
    val executor: CrawlExecutionService,
    val sources: SourceUrlService,
)

interface CrawlStatusService {
    suspend fun snapshot(): CrawlStatusSnapshot
}

sealed interface CrawlStatusSnapshot {
    data object Idle : CrawlStatusSnapshot

    data class Running(
        val activeJobId: String,
        val processedCount: Int,
        val totalCount: Int?,
        val startedAt: Instant,
        val message: String?,
    ) : CrawlStatusSnapshot

    data class Completed(
        val lastJobId: String,
        val completedAt: Instant,
        val processedCount: Int,
        val failureCount: Int,
        val notes: String?,
    ) : CrawlStatusSnapshot
}

interface CrawlScheduleService {
    suspend fun list(): List<CrawlSchedule>

    suspend fun upsert(request: UpsertScheduleRequest): CrawlSchedule

    suspend fun delete(id: String): Boolean
}

data class CrawlSchedule(
    val id: String,
    val cron: String,
    val enabled: Boolean,
    val description: String?,
)

data class UpsertScheduleRequest(
    val id: String?,
    val cron: String,
    val description: String?,
)

interface CrawlExecutionService {
    suspend fun trigger(requestedBy: String): CrawlTriggerResult
}

data class CrawlTriggerResult(
    val accepted: Boolean,
    val jobId: String?,
    val message: String?,
)

interface SourceUrlService {
    suspend fun list(): List<SourceUrlRecord>

    suspend fun add(request: AddSourceUrlRequest): SourceUrlRecord

    suspend fun remove(id: String): Boolean
}

data class SourceUrlRecord(
    val id: Int,
    val url: String,
    val parserType: ParserType,
    val status: CrawlStatus,
    val lastCrawled: Instant?,
    val etag: String?,
    val lastModified: String?,
    val errorMessage: String?,
)

data class AddSourceUrlRequest(
    val url: String,
    val parserType: ParserType? = null,
)
