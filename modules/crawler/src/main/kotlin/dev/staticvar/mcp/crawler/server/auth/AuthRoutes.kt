package dev.staticvar.mcp.crawler.server.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import dev.staticvar.mcp.crawler.server.config.CrawlerConfig
import io.ktor.http.*
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.*
import io.ktor.server.auth.parseAuthorizationHeader
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

fun Route.authRoutes(config: CrawlerConfig.AuthConfig, tokenProvider: TokenProvider) {
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

        if (contentType.match(ContentType.Application.Json)) {
            val token = tokenProvider.generate(payload.username)
            call.respond(
                HttpStatusCode.OK,
                LoginResponse(
                    status = "ok",
                    tokenType = "Bearer",
                    token = token.value,
                    expiresAt = token.expiresAt.toString()
                )
            )
        } else {
            call.respondRedirect("/dashboard")
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

@Serializable
private data class LoginResponse(
    val status: String,
    val tokenType: String,
    val token: String,
    val expiresAt: String
)

fun Route.protectedRoutes(
    tokenProvider: TokenProvider,
    onUnauthorized: suspend ApplicationCall.() -> Unit = { respondRedirect("/login") },
    block: Route.() -> Unit
) {
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
                if (session != null) {
                    call.attributes.put(CurrentSessionKey, session)
                    return@intercept
                }

                val bearerToken = call.request.extractBearerToken()
                if (bearerToken != null) {
                    val claims = tokenProvider.verify(bearerToken)
                    if (claims != null) {
                        call.attributes.put(CurrentSessionKey, CrawlerSession(username = claims.username))
                        return@intercept
                    }
                }

                onUnauthorized(call)
                finish()
            }
        }
        block()
    }
}

private fun ApplicationRequest.extractBearerToken(): String? {
    val header = parseAuthorizationHeader() as? HttpAuthHeader.Single ?: return null
    return header.takeIf { it.authScheme.equals("Bearer", ignoreCase = true) }?.blob
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
