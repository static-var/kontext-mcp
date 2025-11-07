package dev.staticvar.mcp.crawler.server.routes

import dev.staticvar.mcp.crawler.server.auth.currentSession
import dev.staticvar.mcp.crawler.server.service.CrawlStatusService
import dev.staticvar.mcp.crawler.server.service.CrawlStatusSnapshot
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.ul

fun Route.dashboardRoutes(statusService: CrawlStatusService) {
    get("/dashboard") {
        val session = call.currentSession()
        val status = statusService.snapshot()
        call.respondHtml(HttpStatusCode.OK) {
            body {
                h1 { +"Crawler Dashboard" }
                p { +"Signed in as ${session.username}" }
                div {
                    when (status) {
                        is CrawlStatusSnapshot.Idle -> {
                            p { +"Crawler is idle." }
                        }
                        is CrawlStatusSnapshot.Running -> {
                            val processed = status.processedCount
                            val total = status.totalCount ?: -1
                            val time = status.startedAt.asUtcString()
                            p { +"Active job ${status.activeJobId} started at $time" }
                            p { +"Processed $processed of ${if (total >= 0) total else "unknown"}" }
                            status.message?.let { msg -> p { +msg } }
                        }
                        is CrawlStatusSnapshot.Completed -> {
                            val finished = status.completedAt.asUtcString()
                            p { +"Last job ${status.lastJobId} finished at $finished" }
                            p { +"Processed ${status.processedCount} (failures: ${status.failureCount})" }
                            status.notes?.let { note -> p { +note } }
                        }
                    }
                }
                ul {
                    li { +"Trigger a crawl via /crawl/trigger" }
                    li { +"Manage schedules at /crawl/schedules" }
                    li { +"Update URLs at /urls" }
                }
            }
        }
    }
}

private fun Instant.asUtcString(): String =
    toLocalDateTime(TimeZone.UTC).toString() + "Z"
