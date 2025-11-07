package dev.staticvar.mcp.indexer.repository

import dev.staticvar.mcp.shared.model.Document

interface DocumentRepository {
    suspend fun insert(document: Document): Int
    suspend fun findById(id: Int): Document?
    suspend fun findBySourceUrl(sourceUrl: String): List<Document>
    suspend fun deleteBySourceUrl(sourceUrl: String)
}
