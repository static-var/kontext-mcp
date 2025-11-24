package dev.staticvar.mcp.indexer.repository

import dev.staticvar.mcp.shared.model.CrawlStatus
import dev.staticvar.mcp.shared.model.ParserType
import dev.staticvar.mcp.shared.model.SourceUrl

interface SourceUrlRepository {
    suspend fun insert(
        url: String,
        parserType: ParserType,
    ): SourceUrl

    suspend fun findById(id: Int): SourceUrl?

    suspend fun findByUrl(url: String): SourceUrl?

    suspend fun findAll(): List<SourceUrl>

    suspend fun findEnabled(): List<SourceUrl>

    suspend fun findPending(limit: Int = 100): List<SourceUrl>

    suspend fun updateStatus(
        id: Int,
        status: CrawlStatus,
        errorMessage: String? = null,
    )

    suspend fun updateCrawlMetadata(
        id: Int,
        etag: String?,
        lastModified: String?,
    )

    suspend fun markCrawled(id: Int)

    suspend fun resetAllToPending()

    suspend fun delete(id: Int): Boolean
}
