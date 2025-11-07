package dev.staticvar.mcp.crawler.server.routes

import dev.staticvar.mcp.crawler.server.auth.currentSession
import dev.staticvar.mcp.crawler.server.service.AddSourceUrlRequest
import dev.staticvar.mcp.crawler.server.service.CrawlSchedule
import dev.staticvar.mcp.crawler.server.service.CrawlStatusService
import dev.staticvar.mcp.crawler.server.service.CrawlStatusSnapshot
import dev.staticvar.mcp.crawler.server.service.CrawlerServices
import dev.staticvar.mcp.crawler.server.service.SourceUrlRecord
import dev.staticvar.mcp.crawler.server.service.UpsertScheduleRequest
import dev.staticvar.mcp.shared.model.ParserType
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

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

    post("/crawl/schedule") {
        val payload = if (call.request.contentType().match(ContentType.Application.Json)) {
            call.receive<SchedulePayload>()
        } else {
            val params = call.receiveParameters()
            SchedulePayload(
                id = params["id"],
                cron = params["cron"].orEmpty(),
                description = params["description"]
            )
        }

        if (payload.cron.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Cron expression required"))
            return@post
        }

        val schedule = services.scheduler.upsert(
            UpsertScheduleRequest(
                id = payload.id,
                cron = payload.cron,
                description = payload.description
            )
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
        val payload = if (call.request.contentType().match(ContentType.Application.Json)) {
            call.receive<UrlPayload>()
        } else {
            val params = call.receiveParameters()
            UrlPayload(
                url = params["url"].orEmpty(),
                parserType = params["parserType"]
            )
        }

        if (payload.url.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "URL required"))
            return@post
        }

        val parserType = payload.parserType?.let { value ->
            runCatching { ParserType.valueOf(value) }.getOrNull()
        }

        val record = services.sources.add(
            AddSourceUrlRequest(url = payload.url, parserType = parserType)
        )

        if (call.request.contentType().match(ContentType.Application.Json)) {
            call.respond(HttpStatusCode.Created, record.toPayload())
        } else {
            call.respondRedirect("/urls")
        }
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
}

@Serializable
private data class SchedulePayload(
    val id: String?,
    val cron: String,
    val description: String?
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
    val errorMessage: String? = null
)

@Serializable
private data class CrawlStatusResponse(
    val state: String,
    val details: Map<String, String?>
)

@Serializable
private data class CrawlTriggerResponse(
    val accepted: Boolean,
    val jobId: String?,
    val message: String?
)

private fun CrawlStatusSnapshot.toResponse(): CrawlStatusResponse = when (this) {
    CrawlStatusSnapshot.Idle -> CrawlStatusResponse(
        state = "idle",
        details = emptyMap()
    )

    is CrawlStatusSnapshot.Running -> CrawlStatusResponse(
        state = "running",
        details = mapOf(
            "jobId" to activeJobId,
            "processed" to processedCount.toString(),
            "total" to (totalCount?.toString()),
            "startedAt" to startedAt.toString(),
            "message" to message
        )
    )

    is CrawlStatusSnapshot.Completed -> CrawlStatusResponse(
        state = "completed",
        details = mapOf(
            "jobId" to lastJobId,
            "completedAt" to completedAt.toString(),
            "processed" to processedCount.toString(),
            "failures" to failureCount.toString(),
            "notes" to notes
        )
    )
}

private fun CrawlSchedule.toPayload(): SchedulePayload = SchedulePayload(
    id = id,
    cron = cron,
    description = description
)

private fun SourceUrlRecord.toPayload(): UrlPayload = UrlPayload(
    id = id,
    url = url,
    parserType = parserType.name,
    status = status.name,
    lastCrawled = lastCrawled?.toString(),
    etag = etag,
    lastModified = lastModified,
    errorMessage = errorMessage
)

private fun dev.staticvar.mcp.crawler.server.service.CrawlTriggerResult.toResponse(): CrawlTriggerResponse = CrawlTriggerResponse(
    accepted = accepted,
    jobId = jobId,
    message = message
)
