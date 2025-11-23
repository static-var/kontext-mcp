package dev.staticvar.mcp.shared.model

import kotlinx.datetime.serializers.InstantIso8601Serializer
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Represents a source URL to be crawled and indexed.
 * Tracks crawl status, change detection metadata, and parser configuration.
 */
@Serializable
data class SourceUrl(
    val id: Int,
    val url: String,
    val parserType: ParserType,
    val enabled: Boolean,
    @Serializable(with = InstantIso8601Serializer::class)
    val lastCrawled: Instant?,
    val etag: String?,
    val lastModified: String?,
    val status: CrawlStatus,
    val errorMessage: String? = null,
)
