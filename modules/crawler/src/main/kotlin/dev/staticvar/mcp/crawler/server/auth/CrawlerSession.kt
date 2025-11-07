package dev.staticvar.mcp.crawler.server.auth

import kotlinx.serialization.Serializable

@Serializable
data class CrawlerSession(
    val username: String
)
