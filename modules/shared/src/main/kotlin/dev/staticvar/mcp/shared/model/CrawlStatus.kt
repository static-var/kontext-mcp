package dev.staticvar.mcp.shared.model

import kotlinx.serialization.Serializable

/**
 * Status of a source URL crawl operation.
 */
@Serializable
enum class CrawlStatus {
    /** URL has never been crawled */
    PENDING,

    /** Crawl is currently in progress */
    IN_PROGRESS,

    /** Crawl completed successfully */
    SUCCESS,

    /** Crawl failed due to an error */
    FAILED,

    /** URL temporarily disabled */
    DISABLED
}
