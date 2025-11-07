package dev.staticvar.mcp.crawler.server.routes

import dev.staticvar.mcp.crawler.server.auth.currentSession
import dev.staticvar.mcp.crawler.server.service.CrawlerServices
import io.ktor.http.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.ButtonType
import kotlinx.html.FormMethod
import kotlinx.html.body
import kotlinx.html.br
import kotlinx.html.button
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.textInput
import kotlinx.html.ul
import kotlinx.html.style

fun Route.managementRoutes(services: CrawlerServices) {
    get("/crawl/trigger") {
        val session = call.currentSession()
        call.respondHtml(HttpStatusCode.OK) {
            body {
                h1 { +"Trigger Crawl" }
                p { +"Signed in as ${session.username}" }
                form(action = "/api/crawl/start", method = FormMethod.post) {
                    button(type = ButtonType.submit) { +"Start crawl" }
                }
            }
        }
    }

    get("/crawl/schedules") {
        val schedules = services.scheduler.list()
        call.respondHtml(HttpStatusCode.OK) {
            body {
                h1 { +"Crawl Schedules" }
                ul {
                    schedules.forEach { schedule ->
                        li {
                            +"${schedule.id}: ${schedule.cron} (enabled=${schedule.enabled})"
                        }
                    }
                }
                h2 { +"Create or update" }
                form(action = "/api/crawl/schedule", method = FormMethod.post) {
                    p { +"Leave ID empty to create a new schedule." }
                    textInput(name = "id") { placeholder = "Schedule ID" }
                    textInput(name = "cron") {
                        placeholder = "Cron expression"
                        required = true
                    }
                    textInput(name = "description") { placeholder = "Optional description" }
                    button(type = ButtonType.submit) { +"Save" }
                }
            }
        }
    }

    post("/crawl/schedules/delete") {
        val form = call.receiveParameters()
        val id = form["id"].orEmpty()
        if (id.isBlank()) {
            call.respondRedirect("/crawl/schedules")
            return@post
        }
        services.scheduler.delete(id)
        call.respondRedirect("/crawl/schedules")
    }

    get("/urls") {
        val urls = services.sources.list()
        call.respondHtml(HttpStatusCode.OK) {
            body {
                h1 { +"Source URLs" }
                ul {
                    urls.forEach { source ->
                        li {
                            +"${source.id}: ${source.url}"
                            br {}
                            +"Parser: ${source.parserType} | Status: ${source.status}"
                            source.lastCrawled?.let { timestamp ->
                                br {}
                                +"Last crawled: $timestamp"
                            }
                            source.errorMessage?.let { error ->
                                br {}
                                span {
                                    style = "color: #aa0000;"
                                    +"Error: $error"
                                }
                            }
                        }
                    }
                }
                h2 { +"Add new" }
                form(action = "/api/urls", method = FormMethod.post) {
                    textInput(name = "url") {
                        placeholder = "https://example.com/docs"
                        required = true
                    }
                    textInput(name = "parserType") { placeholder = "Optional parser type (e.g. ANDROID_DOCS)" }
                    button(type = ButtonType.submit) { +"Add" }
                }
            }
        }
    }

    post("/urls/delete") {
        val form = call.receiveParameters()
        val id = form["id"].orEmpty()
        if (id.isNotBlank()) {
            services.sources.remove(id)
        }
        call.respondRedirect("/urls")
    }
}
