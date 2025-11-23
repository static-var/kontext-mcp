package dev.staticvar.mcp.embedder.service

import dev.staticvar.mcp.embedder.service.model.EmbeddingBatchRequest
import dev.staticvar.mcp.embedder.service.model.EmbeddingResult

/**
 * Contract for generating vector embeddings for textual inputs.
 * Implementations may rely on different backends (ONNX Runtime, remote APIs, etc.).
 */
interface EmbeddingService : AutoCloseable {
    /**
     * Dimension of the produced embedding vectors.
     */
    val dimension: Int

    /**
     * Maximum number of tokens accepted by the underlying model.
     */
    val maxTokens: Int

    /**
     * Generates embeddings for the provided [EmbeddingBatchRequest].
     *
     * @return ordered list of [EmbeddingResult] corresponding to each request item.
     */
    suspend fun embed(request: EmbeddingBatchRequest): List<EmbeddingResult>

    override fun close() {
        // default no-op
    }
}
