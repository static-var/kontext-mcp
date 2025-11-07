package dev.staticvar.mcp.crawler.server.auth

import io.ktor.server.application.*
import io.ktor.util.*

val CurrentSessionKey = AttributeKey<CrawlerSession>("crawler-session")

fun ApplicationCall.currentSession(): CrawlerSession = attributes[CurrentSessionKey]
