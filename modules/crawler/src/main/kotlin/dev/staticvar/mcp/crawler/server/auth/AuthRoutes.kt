package dev.staticvar.mcp.crawler.server.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import dev.staticvar.mcp.crawler.server.config.CrawlerConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.pipeline.*
import kotlinx.html.*
import kotlinx.serialization.Serializable

@Serializable
private data class LoginRequest(
    val username: String,
    val password: String
)

fun Route.authRoutes(config: CrawlerConfig.AuthConfig) {
    get("/login") {
        val error = call.request.queryParameters["error"]
        call.respondHtml(HttpStatusCode.OK) {
            loginPage(errorMessage = error)
        }
    }

    post("/login") {
        val contentType = call.request.contentType()
        val payload = when {
            contentType.match(ContentType.Application.Json) -> runCatching {
                call.receive<LoginRequest>()
            }.getOrElse {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid payload"))
                return@post
            }
            else -> {
                val params = call.receiveParameters()
                LoginRequest(
                    username = params["username"].orEmpty(),
                    password = params["password"].orEmpty()
                )
            }
        }

        val usernameMatches = payload.username == config.username
        val passwordMatches = BCrypt.verifyer()
            .verify(payload.password.toCharArray(), config.passwordBcrypt)
            .verified

        if (!usernameMatches || !passwordMatches) {
            when {
                contentType.match(ContentType.Application.Json) -> call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Invalid credentials")
                )
                else -> call.respondRedirect("/login?error=Invalid+credentials")
            }
            return@post
        }

        call.sessions.set(CrawlerSession(username = payload.username))

        when {
            contentType.match(ContentType.Application.Json) -> call.respond(
                HttpStatusCode.OK,
                mapOf("status" to "ok")
            )
            else -> call.respondRedirect("/dashboard")
        }
    }

    post("/logout") {
        call.sessions.clear<CrawlerSession>()
        if (call.request.contentType().match(ContentType.Application.Json)) {
            call.respond(HttpStatusCode.OK, mapOf("status" to "logged_out"))
        } else {
            call.respondRedirect("/login")
        }
    }
}

fun Route.protectedRoutes(onUnauthorized: suspend ApplicationCall.() -> Unit = { respondRedirect("/login") }, block: Route.() -> Unit) {
    route("") {
        intercept(ApplicationCallPipeline.Plugins) {
            // Allowlist paths that must remain publicly accessible to avoid redirect loops
            // and to expose health endpoints without authentication.
            val path = call.request.path()
            val isPublic = when {
                path == "/login" -> true
                path == "/logout" -> true
                path == "/healthz" -> true
                path == "/" -> true
                else -> false
            }

            if (!isPublic) {
                val session = call.sessions.get<CrawlerSession>()
                if (session == null) {
                    onUnauthorized(call)
                    finish()
                } else {
                    call.attributes.put(CurrentSessionKey, session)
                }
            }
        }
        block()
    }
}

private fun HTML.loginPage(errorMessage: String?) {
    head {
        title { +"MCP Crawler Login" }
        meta { charset = "utf-8" }
    }
    body {
        h1 { +"Crawler Control" }
        if (!errorMessage.isNullOrBlank()) {
            p {
                style = "color: red;"
                +errorMessage
            }
        }
        form(action = "/login", method = FormMethod.post) {
            label {
                htmlFor = "username"
                +"Username"
            }
            textInput(name = "username") {
                id = "username"
                required = true
            }
            br {}
            label {
                htmlFor = "password"
                +"Password"
            }
            passwordInput(name = "password") {
                id = "password"
                required = true
            }
            br {}
            submitInput { value = "Sign in" }
        }
    }
}
