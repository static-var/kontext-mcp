package dev.staticvar.mcp.embedder.service

/**
 * Service for reranking search results using a cross-encoder model.
 */
interface RerankerService : AutoCloseable {

    /**
     * Reranks a list of documents against a query.
     *
     * @param query The search query.
     * @param documents The list of document contents to rerank.
     * @return A list of indices from the original [documents] list, sorted by relevance (descending).
     */
    suspend fun rerank(query: String, documents: List<String>): List<Int>

    override fun close() {
        // default no-op
    }
}
