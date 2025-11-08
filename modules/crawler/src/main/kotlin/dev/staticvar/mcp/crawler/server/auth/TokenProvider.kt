package dev.staticvar.mcp.crawler.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.time.Instant
import java.util.Date

/**
 * Minimal interface for issuing and validating bearer tokens.
 */
interface TokenProvider {
    data class Token(val value: String, val issuedAt: Instant, val expiresAt: Instant)
    data class Claims(val username: String)

    fun generate(username: String): Token
    fun verify(token: String): Claims?
}

class JwtTokenProvider(
    secret: String,
    private val issuer: String = "mcp-crawler",
    private val tokenTtlSeconds: Long = 3600
) : TokenProvider {

    private val algorithm = Algorithm.HMAC256(secret)
    private val verifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .build()

    override fun generate(username: String): TokenProvider.Token {
        val issuedAt = Instant.now()
        val expiresAt = issuedAt.plusSeconds(tokenTtlSeconds)
        val jwt = JWT.create()
            .withIssuer(issuer)
            .withSubject(username)
            .withIssuedAt(Date.from(issuedAt))
            .withExpiresAt(Date.from(expiresAt))
            .sign(algorithm)

        return TokenProvider.Token(jwt, issuedAt, expiresAt)
    }

    override fun verify(token: String): TokenProvider.Claims? =
        runCatching { verifier.verify(token) }
            .getOrNull()
            ?.let { decoded ->
                val username = decoded.subject ?: return@let null
                TokenProvider.Claims(username)
            }
}
