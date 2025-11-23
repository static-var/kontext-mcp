package dev.staticvar.mcp.shared.model

import kotlinx.serialization.Serializable

/**
 * Request to search documentation.
 *
 * @property query Natural language search query
 * @property tokenBudget Maximum tokens for response (null = use default)
 * @property filters Metadata filters for refined search (e.g., {"api_level": "34"})
 * @property similarityThreshold Minimum cosine similarity (0.0-1.0)
 */
@Serializable
data class SearchRequest(
    val query: String,
    val tokenBudget: Int? = null,
    val filters: Map<String, String>? = null,
    val similarityThreshold: Float = 0.7f,
)

/**
 * Search response containing relevant documentation chunks.
 *
 * @property chunks Retrieved and ranked chunks within token budget
 * @property totalTokens Actual tokens used in response
 * @property confidence Average similarity score of returned chunks
 */
@Serializable
data class SearchResponse(
    val chunks: List<RetrievedChunk>,
    val totalTokens: Int,
    val confidence: Float,
)

/**
 * A single retrieved documentation chunk with provenance.
 *
 * @property content Chunk text content
 * @property source Original URL or document identifier
 * @property similarity Cosine similarity score (0.0-1.0)
 * @property metadata Associated metadata (version, API level, etc.)
 */
@Serializable
data class RetrievedChunk(
    val content: String,
    val source: String,
    val similarity: Float,
    val metadata: Map<String, String>,
)
