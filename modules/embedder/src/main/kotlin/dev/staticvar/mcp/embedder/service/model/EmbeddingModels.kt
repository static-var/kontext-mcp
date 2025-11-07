package dev.staticvar.mcp.embedder.service.model

/**
 * Batch container for embedding generation.
 */
data class EmbeddingBatchRequest(
    val texts: List<String>
) {
    init {
        require(texts.isNotEmpty()) { "Embedding batch cannot be empty" }
    }
}

/**
 * Result data for a single embedded text.
 *
 * @property embedding normalized embedding vector.
 * @property tokenCount number of tokens consumed (before padding).
 * @property truncated indicates whether the text exceeded the model max token limit.
 */
data class EmbeddingResult(
    val embedding: FloatArray,
    val tokenCount: Int,
    val truncated: Boolean
)
