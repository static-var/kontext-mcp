package dev.staticvar.mcp.crawler.server

import dev.staticvar.mcp.crawler.server.auth.CrawlerSession
import dev.staticvar.mcp.crawler.server.auth.JwtTokenProvider
import dev.staticvar.mcp.crawler.server.auth.authRoutes
import dev.staticvar.mcp.crawler.server.auth.protectedRoutes
import dev.staticvar.mcp.crawler.server.config.CrawlerConfig
import dev.staticvar.mcp.crawler.server.routes.apiRoutes
import dev.staticvar.mcp.crawler.server.service.CrawlStatusSnapshot
import dev.staticvar.mcp.crawler.server.service.CrawlerServices
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import java.io.File
import kotlin.time.Duration.Companion.seconds

fun Application.crawlerModule(
    config: CrawlerConfig,
    services: CrawlerServices,
) {
    val tokenProvider =
        JwtTokenProvider(
            secret = config.auth.tokenSecret,
            tokenTtlSeconds = config.auth.tokenTtlSeconds,
        )
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = false
                ignoreUnknownKeys = true
                explicitNulls = false
            },
        )
    }

    install(CallLogging) {
        level = Level.INFO
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            this@crawlerModule.environment.log.error("Unhandled error", cause)
            if (call.wantsJson()) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (cause.message ?: "unexpected error")),
                )
            } else {
                call.respond(HttpStatusCode.InternalServerError, "Something went wrong")
            }
        }
    }

    install(Sessions) {
        val sessionConfig = config.session
        cookie<CrawlerSession>(sessionConfig.cookieName) {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.secure = sessionConfig.enforceSecureCookie
            cookie.maxAge = sessionConfig.maxAgeSeconds.seconds
            cookie.extensions["SameSite"] = "Lax"
            serializer = UsernameSessionSerializer
            transform(SessionTransportTransformerMessageAuthentication(sessionConfig.secret.encodeToByteArray()))
        }
    }

    routing {
        val staticDir = File("/app/static")
        if (staticDir.exists() && staticDir.isDirectory) {
            // Manual route to avoid potential zero-copy issues with staticFiles in Docker
            route("/assets") {
                get("/{path...}") {
                    val path = call.parameters.getAll("path")?.joinToString("/")
                    if (path != null) {
                        val file = File(staticDir, "assets/$path")
                        if (file.exists() && file.isFile) {
                            call.respondBytes(file.readBytes(), ContentType.defaultForFile(file))
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }
                }
                head("/{path...}") {
                    val path = call.parameters.getAll("path")?.joinToString("/")
                    if (path != null) {
                        val file = File(staticDir, "assets/$path")
                        if (file.exists() && file.isFile) {
                            call.respondBytes(file.readBytes(), ContentType.defaultForFile(file))
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }
                }
            }
        } else {
            // Serve the Vite SPA from classpath resources
            staticResources("/", "static", index = "index.html")
            staticResources("/assets", "static/assets")
        }

        // Handle SPA client-side routing by serving index.html for unknown paths
        // excluding /api, /healthz, etc. which are handled by other routes.
        // Note: This simple fallback might need adjustment if you have deep links.
        // For now, let's rely on staticResources serving the files and index.html.
        // If client-side routing is needed for deep links, we might need a wildcard route at the end.

        get("/healthz") {
            val snapshot = services.status.snapshot()
            val state =
                when (snapshot) {
                    is CrawlStatusSnapshot.Running -> "running"
                    is CrawlStatusSnapshot.Completed -> "completed"
                    CrawlStatusSnapshot.Idle -> "idle"
                }
            call.respond(HttpStatusCode.OK, mapOf("status" to state))
        }

        get("/readyz") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ready"))
        }

        authRoutes(config.auth, tokenProvider)

        route("/api/session") {
            get {
                val session = call.sessions.get<CrawlerSession>()
                if (session != null) {
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf("authenticated" to true, "username" to session.username),
                    )
                } else {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("authenticated" to false))
                }
            }
        }

        protectedRoutes(tokenProvider, onUnauthorized = {
            if (wantsJson()) {
                respond(HttpStatusCode.Unauthorized, mapOf("error" to "authentication required"))
            } else {
                respondRedirect("/login")
            }
        }) {
            route("/api") {
                apiRoutes(services)
            }
        }

        // SPA Fallback for client-side routing
        get("/{...}") {
            this@crawlerModule.environment.log.info("Fallback hit for: ${call.request.uri}")
            // Check if it's an API call to avoid returning HTML for API 404s
            if (call.request.path().startsWith("/api")) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                val staticDir = File("/app/static")
                if (staticDir.exists() && staticDir.isDirectory) {
                    val indexFile = File(staticDir, "index.html")
                    if (indexFile.exists()) {
                        call.respondFile(indexFile)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Index page not found")
                    }
                } else {
                    val indexPage = this::class.java.classLoader.getResourceAsStream("static/index.html")?.readAllBytes()
                    if (indexPage != null) {
                        call.respondBytes(indexPage, ContentType.Text.Html)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Index page not found")
                    }
                }
            }
        }
    }
}

private fun ApplicationCall.wantsJson(): Boolean {
    val acceptValues = request.headers.getAll(HttpHeaders.Accept).orEmpty()
    val expectsJson =
        acceptValues.any { header ->
            header.split(',').any { entry ->
                entry.trim().startsWith(ContentType.Application.Json.toString(), ignoreCase = true)
            }
        }

    val secFetchDest = request.headers["Sec-Fetch-Dest"]?.lowercase()
    val secFetchMode = request.headers["Sec-Fetch-Mode"]?.lowercase()
    val isAjax = request.headers["X-Requested-With"]?.equals("xmlhttprequest", ignoreCase = true) == true
    val isProgrammaticFetch =
        secFetchDest == "empty" || secFetchMode == "cors" || secFetchMode == "same-origin"

    return expectsJson || isAjax || isProgrammaticFetch
}

private object UsernameSessionSerializer : SessionSerializer<CrawlerSession> {
    override fun deserialize(text: String): CrawlerSession = CrawlerSession(username = text)

    override fun serialize(session: CrawlerSession): String = session.username
}
