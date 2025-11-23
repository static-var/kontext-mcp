package dev.staticvar.mcp.indexer.repository

import dev.staticvar.mcp.shared.model.EmbeddedChunk

interface EmbeddingRepository {
    suspend fun insertBatch(chunks: List<EmbeddedChunk>)

    suspend fun search(
        queryEmbedding: FloatArray,
        limit: Int,
        similarityThreshold: Float,
        filters: Map<String, String>? = null,
    ): List<ScoredChunk>

    suspend fun deleteByDocumentId(documentId: Int)
}

data class ScoredChunk(
    val content: String,
    val sourceUrl: String,
    val similarity: Float,
    val metadata: Map<String, String>,
    val tokenCount: Int,
)
