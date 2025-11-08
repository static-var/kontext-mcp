package dev.staticvar.mcp.crawler.server.config

import io.ktor.server.config.*

/**
 * Application configuration for the crawler web server.
 */
data class CrawlerConfig(
    val auth: AuthConfig,
    val session: SessionConfig,
    val indexer: IndexerConfig
) {
    data class AuthConfig(
        val username: String,
        val passwordBcrypt: String,
        val tokenSecret: String,
        val tokenTtlSeconds: Long
    )

    data class SessionConfig(
        val secret: String,
        val cookieName: String,
        val enforceSecureCookie: Boolean,
        val maxAgeSeconds: Long
    )

    data class IndexerConfig(
        val baseUrl: String
    )

    companion object {
        fun load(config: ApplicationConfig): CrawlerConfig {
            val crawlerConfig = config.config("crawler")
            val sessionConfig = crawlerConfig.config("session")
            val indexerConfig = crawlerConfig.config("indexer")

            val session = SessionConfig(
                secret = sessionConfig.property("secret").getString(),
                cookieName = sessionConfig.propertyOrNull("cookie-name")?.getString()
                    ?: "mcp_crawler_session",
                enforceSecureCookie = sessionConfig.propertyOrNull("cookie-secure")?.getString()?.toBooleanStrictOrNull()
                    ?: false,
                maxAgeSeconds = sessionConfig.propertyOrNull("max-age-seconds")?.getString()?.toLongOrNull()
                    ?: 3 * 60 * 60L
            )

            val authConfig = crawlerConfig.config("auth")

            val auth = AuthConfig(
                username = authConfig.property("username").getString(),
                passwordBcrypt = authConfig.property("password-bcrypt").getString(),
                tokenSecret = authConfig.propertyOrNull("token-secret")?.getString()
                    ?: session.secret,
                tokenTtlSeconds = authConfig.propertyOrNull("token-ttl-seconds")?.getString()?.toLongOrNull()
                    ?: 3600L
            )

            val indexer = IndexerConfig(
                baseUrl = indexerConfig.property("base-url").getString()
            )

            return CrawlerConfig(
                auth = auth,
                session = session,
                indexer = indexer
            )
        }
    }
}
