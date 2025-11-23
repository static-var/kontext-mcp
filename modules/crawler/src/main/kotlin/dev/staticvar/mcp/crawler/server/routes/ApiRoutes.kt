package dev.staticvar.mcp.crawler.server.routes

import dev.staticvar.mcp.crawler.server.auth.currentSession
import dev.staticvar.mcp.crawler.server.service.AddSourceUrlRequest
import dev.staticvar.mcp.crawler.server.service.CrawlSchedule
import dev.staticvar.mcp.crawler.server.service.CrawlStatusSnapshot
import dev.staticvar.mcp.crawler.server.service.CrawlerServices
import dev.staticvar.mcp.crawler.server.service.SourceUrlRecord
import dev.staticvar.mcp.crawler.server.service.UpsertScheduleRequest
import dev.staticvar.mcp.shared.model.CrawlStatus
import dev.staticvar.mcp.shared.model.ParserType
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlin.time.toEpochMilliseconds

fun Route.apiRoutes(services: CrawlerServices) {
    post("/crawl/start") {
        val session = call.currentSession()
        val result = services.executor.trigger(session.username)
        if (call.request.contentType().match(ContentType.Application.Json)) {
            call.respond(HttpStatusCode.Accepted, result.toResponse())
        } else {
            call.respondRedirect("/dashboard")
        }
    }

    get("/crawl/status") {
        val status = services.status.snapshot()
        call.respond(HttpStatusCode.OK, status.toResponse())
    }

    get("/crawl/insights") {
        val snapshot = services.status.snapshot().toResponse()
        val urls = services.sources.list()

        val insights =
            urls
                .sortedWith(
                    compareByDescending<SourceUrlRecord> { it.status == CrawlStatus.IN_PROGRESS }
                        .thenByDescending { it.lastCrawled?.toEpochMilliseconds() ?: Long.MIN_VALUE },
                )
                .take(50)
                .map { it.toInsightPayload() }

        val summary = urls.toQueueSummary()

        call.respond(
            HttpStatusCode.OK,
            CrawlerInsightsPayload(
                status = snapshot,
                queueSummary = summary,
                recentSources = insights,
            ),
        )
    }

    post("/crawl/schedule") {
        val payload =
            when {
                call.isJsonRequest() -> call.receiveJsonOrNull<SchedulePayload>() ?: return@post
                call.isFormRequest() -> {
                    val params = call.receiveParameters()
                    SchedulePayload(
                        id = params["id"],
                        cron = params["cron"].orEmpty(),
                        description = params["description"],
                    )
                }
                !call.hasRequestBody() -> {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Request body required"))
                    return@post
                }
                else -> {
                    call.respond(
                        HttpStatusCode.UnsupportedMediaType,
                        mapOf("error" to "Use application/json or application/x-www-form-urlencoded"),
                    )
                    return@post
                }
            }

        if (payload.cron.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Cron expression required"))
            return@post
        }

        val schedule =
            services.scheduler.upsert(
                UpsertScheduleRequest(
                    id = payload.id,
                    cron = payload.cron,
                    description = payload.description,
                ),
            )

        if (call.request.contentType().match(ContentType.Application.Json)) {
            call.respond(HttpStatusCode.OK, schedule.toPayload())
        } else {
            call.respondRedirect("/crawl/schedules")
        }
    }

    get("/crawl/schedule") {
        val schedules = services.scheduler.list().map { it.toPayload() }
        call.respond(HttpStatusCode.OK, schedules)
    }

    delete("/crawl/schedule/{id}") {
        val id = call.parameters["id"].orEmpty()
        val deleted = services.scheduler.delete(id)
        if (!deleted) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Schedule not found"))
        } else {
            call.respond(HttpStatusCode.OK, mapOf("status" to "deleted"))
        }
    }

    get("/urls") {
        val urls = services.sources.list().map { it.toPayload() }
        call.respond(HttpStatusCode.OK, urls)
    }

    post("/urls") {
        val payload =
            when {
                call.isJsonRequest() -> call.receiveJsonOrNull<UrlPayload>()?.normalize() ?: return@post
                call.isFormRequest() -> {
                    val params = call.receiveParameters()
                    UrlPayload(
                        url = params["url"].orEmpty(),
                        parserType = params["parserType"].takeUnless { it.isNullOrBlank() },
                    )
                }
                !call.hasRequestBody() -> {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Request body required"))
                    return@post
                }
                else -> {
                    call.respond(
                        HttpStatusCode.UnsupportedMediaType,
                        mapOf("error" to "Use application/json or application/x-www-form-urlencoded"),
                    )
                    return@post
                }
            }

        if (payload.url.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "URL required"))
            return@post
        }

        val parserType =
            payload.parserType?.takeUnless { it.isBlank() }?.let { value ->
                runCatching { ParserType.valueOf(value) }.getOrElse {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid parser type: $value. Allowed: ${ParserType.values().joinToString()}"),
                    )
                    return@post
                }
            }

        val record =
            services.sources.add(
                AddSourceUrlRequest(url = payload.url, parserType = parserType),
            )

        if (call.request.contentType().match(ContentType.Application.Json)) {
            call.respond(HttpStatusCode.Created, record.toPayload())
        } else {
            call.respondRedirect("/urls")
        }
    }

    post("/urls/bulk") {
        if (!call.isJsonRequest()) {
            call.respond(
                HttpStatusCode.UnsupportedMediaType,
                mapOf("error" to "Use application/json for bulk requests"),
            )
            return@post
        }

        val payload = call.receiveJsonOrNull<BulkUrlPayload>() ?: return@post
        val entries = payload.entries.map { it.normalize() }.filter { it.url.isNotBlank() }
        if (entries.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No URLs supplied"))
            return@post
        }

        val successes = mutableListOf<UrlPayload>()
        val failures = mutableListOf<BulkUrlFailure>()

        for (entry in entries) {
            val parserType =
                entry.parserType?.let { raw ->
                    runCatching { ParserType.valueOf(raw) }
                        .getOrElse {
                            failures += BulkUrlFailure(entry.url, "Invalid parser type: $raw")
                            null
                        }
                }

            if (entry.parserType != null && parserType == null) {
                continue
            }

            try {
                val record =
                    services.sources.add(
                        AddSourceUrlRequest(
                            url = entry.url,
                            parserType = parserType,
                        ),
                    )
                successes += record.toPayload()
            } catch (ex: Exception) {
                failures += BulkUrlFailure(entry.url, ex.message ?: "Failed to enqueue")
            }
        }

        call.respond(HttpStatusCode.OK, BulkUrlResponse(successes = successes, failures = failures))
    }

    delete("/urls/{id}") {
        val id = call.parameters["id"].orEmpty()
        val removed = services.sources.remove(id)
        if (!removed) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Source URL not found"))
        } else {
            call.respond(HttpStatusCode.OK, mapOf("status" to "deleted"))
        }
    }

    get("/meta/parsers") {
        val parsers = ParserType.entries.map { it.name }
        call.respond(HttpStatusCode.OK, parsers)
    }
}

@Serializable
private data class SchedulePayload(
    val id: String?,
    val cron: String,
    val description: String?,
)

@Serializable
private data class UrlPayload(
    val id: Int? = null,
    val url: String,
    val parserType: String? = null,
    val status: String? = null,
    val lastCrawled: String? = null,
    val etag: String? = null,
    val lastModified: String? = null,
    val errorMessage: String? = null,
)

private fun UrlPayload.normalize(): UrlPayload =
    copy(
        url = url.trim(),
        parserType = parserType?.takeUnless { it.isBlank() },
    )

@Serializable
private data class BulkUrlPayload(val entries: List<UrlPayload>)

@Serializable
private data class BulkUrlResponse(
    val successes: List<UrlPayload>,
    val failures: List<BulkUrlFailure>,
)

@Serializable
private data class BulkUrlFailure(
    val url: String,
    val reason: String,
)

@Serializable
private data class CrawlStatusResponse(
    val state: String,
    val details: Map<String, String?>,
)

@Serializable
private data class CrawlTriggerResponse(
    val accepted: Boolean,
    val jobId: String?,
    val message: String?,
)

@Serializable
private data class QueueSummaryPayload(
    val total: Int,
    val pending: Int,
    val inProgress: Int,
    val success: Int,
    val failed: Int,
    val disabled: Int,
)

@Serializable
private data class CrawlerInsightsPayload(
    val status: CrawlStatusResponse,
    val queueSummary: QueueSummaryPayload,
    val recentSources: List<SourceInsightPayload>,
)

@Serializable
private data class SourceInsightPayload(
    val id: Int,
    val url: String,
    val parserType: String,
    val status: String,
    val lastCrawled: String?,
    val embeddingReady: Boolean,
    val errorMessage: String?,
)

private fun CrawlStatusSnapshot.toResponse(): CrawlStatusResponse =
    when (this) {
        CrawlStatusSnapshot.Idle ->
            CrawlStatusResponse(
                state = "idle",
                details = emptyMap(),
            )

        is CrawlStatusSnapshot.Running ->
            CrawlStatusResponse(
                state = "running",
                details =
                    mapOf(
                        "jobId" to activeJobId,
                        "processed" to processedCount.toString(),
                        "total" to (totalCount?.toString()),
                        "startedAt" to startedAt.toString(),
                        "message" to message,
                    ),
            )

        is CrawlStatusSnapshot.Completed ->
            CrawlStatusResponse(
                state = "completed",
                details =
                    mapOf(
                        "jobId" to lastJobId,
                        "completedAt" to completedAt.toString(),
                        "processed" to processedCount.toString(),
                        "failures" to failureCount.toString(),
                        "notes" to notes,
                    ),
            )
    }

private fun CrawlSchedule.toPayload(): SchedulePayload =
    SchedulePayload(
        id = id,
        cron = cron,
        description = description,
    )

private fun SourceUrlRecord.toPayload(): UrlPayload =
    UrlPayload(
        id = id,
        url = url,
        parserType = parserType.name,
        status = status.name,
        lastCrawled = lastCrawled?.toString(),
        etag = etag,
        lastModified = lastModified,
        errorMessage = errorMessage,
    )

private fun dev.staticvar.mcp.crawler.server.service.CrawlTriggerResult.toResponse(): CrawlTriggerResponse =
    CrawlTriggerResponse(
        accepted = accepted,
        jobId = jobId,
        message = message,
    )

private fun List<SourceUrlRecord>.toQueueSummary(): QueueSummaryPayload {
    val pending = count { it.status == CrawlStatus.PENDING }
    val inProgress = count { it.status == CrawlStatus.IN_PROGRESS }
    val success = count { it.status == CrawlStatus.SUCCESS }
    val failed = count { it.status == CrawlStatus.FAILED }
    val disabled = count { it.status == CrawlStatus.DISABLED }

    return QueueSummaryPayload(
        total = size,
        pending = pending,
        inProgress = inProgress,
        success = success,
        failed = failed,
        disabled = disabled,
    )
}

private fun SourceUrlRecord.toInsightPayload(): SourceInsightPayload =
    SourceInsightPayload(
        id = id,
        url = url,
        parserType = parserType.name,
        status = status.name,
        lastCrawled = lastCrawled?.toString(),
        embeddingReady = status == CrawlStatus.SUCCESS && lastCrawled != null,
        errorMessage = errorMessage,
    )

private fun ApplicationCall.isJsonRequest(): Boolean = request.contentType().withoutParameters().match(ContentType.Application.Json)

private fun ApplicationCall.isFormRequest(): Boolean =
    request.contentType().withoutParameters().match(ContentType.Application.FormUrlEncoded)

private fun ApplicationCall.hasRequestBody(): Boolean = request.contentLength()?.let { it > 0 } ?: true

private suspend inline fun <reified T : Any> ApplicationCall.receiveJsonOrNull(): T? =
    runCatching { receive<T>() }
        .onFailure {
            respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid JSON payload"))
        }
        .getOrNull()
