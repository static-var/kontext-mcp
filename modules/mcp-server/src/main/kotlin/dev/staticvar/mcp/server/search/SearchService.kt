package dev.staticvar.mcp.server.search

import dev.staticvar.mcp.embedder.service.EmbeddingService
import dev.staticvar.mcp.embedder.service.RerankerService
import dev.staticvar.mcp.embedder.service.model.EmbeddingBatchRequest
import dev.staticvar.mcp.indexer.repository.EmbeddingRepository
import dev.staticvar.mcp.indexer.repository.ScoredChunk
import dev.staticvar.mcp.shared.config.RerankingConfig
import dev.staticvar.mcp.shared.config.RetrievalConfig
import dev.staticvar.mcp.shared.model.RetrievedChunk
import dev.staticvar.mcp.shared.model.SearchRequest
import dev.staticvar.mcp.shared.model.SearchResponse
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Coordinates query embedding, vector search, and chunk packing for documentation retrieval.
 */
class SearchService(
    private val embeddingService: EmbeddingService,
    private val embeddingRepository: EmbeddingRepository,
    private val rerankerService: RerankerService,
    private val retrievalConfig: RetrievalConfig,
    private val rerankingConfig: RerankingConfig,
) {
    private val logger = KotlinLogging.logger { }

    suspend fun search(request: SearchRequest): SearchResponse {
        val tokenBudget = resolveTokenBudget(request.tokenBudget)
        val similarityThreshold = resolveSimilarityThreshold(request.similarityThreshold)

        // Prepend instruction for retrieval models like mxbai-embed-large-v1
        // This is harmless for models that don't strictly require it, but essential for those that do.
        val queryText = "Represent this sentence for searching relevant passages: ${request.query}"

        val embeddingResult =
            embeddingService.embed(
                EmbeddingBatchRequest(listOf(queryText)),
            ).firstOrNull() ?: error("Embedding service returned no results for query.")

        if (embeddingResult.truncated) {
            logger.warn { "Query text exceeded embedding token limit (${embeddingService.maxTokens}); search results may degrade." }
        }

        // Fetch more candidates if reranking is enabled to improve recall before precision refinement
        val candidateLimit =
            if (rerankingConfig.enabled) {
                retrievalConfig.topKCandidates * 5
            } else {
                retrievalConfig.topKCandidates
            }

        var scoredChunks =
            embeddingRepository.search(
                queryEmbedding = embeddingResult.embedding,
                limit = candidateLimit,
                similarityThreshold = similarityThreshold,
                filters = request.filters,
            )

        if (rerankingConfig.enabled && scoredChunks.isNotEmpty()) {
            logger.debug { "Reranking ${scoredChunks.size} candidates for query: ${request.query}" }
            val documents = scoredChunks.map { it.content }
            val rerankedIndices = rerankerService.rerank(request.query, documents)

            scoredChunks =
                rerankedIndices.map { scoredChunks[it] }
                    .take(retrievalConfig.topKCandidates)
        }

        val packed = packResults(scoredChunks, tokenBudget)
        val retrievedChunks =
            packed.chunks.map { chunk ->
                RetrievedChunk(
                    content = chunk.content,
                    source = chunk.sourceUrl,
                    similarity = chunk.similarity,
                    metadata = chunk.metadata,
                )
            }

        val confidence = if (retrievedChunks.isEmpty()) 0f else packed.similaritySum / retrievedChunks.size

        return SearchResponse(
            chunks = retrievedChunks,
            totalTokens = packed.totalTokens,
            confidence = confidence,
        )
    }

    private fun resolveTokenBudget(requestedBudget: Int?): Int {
        val boundedDefault = retrievalConfig.defaultTokenBudget.coerceIn(1, retrievalConfig.maxTokenBudget)
        val normalizedRequested = requestedBudget?.coerceIn(1, retrievalConfig.maxTokenBudget)
        return normalizedRequested ?: boundedDefault
    }

    private fun resolveSimilarityThreshold(requestedThreshold: Float): Float {
        if (requestedThreshold in 0f..1f) {
            return requestedThreshold
        }
        return retrievalConfig.defaultSimilarityThreshold.coerceIn(0f, 1f)
    }

    private fun packResults(
        chunks: List<ScoredChunk>,
        tokenBudget: Int,
    ): PackedResults {
        if (chunks.isEmpty()) return PackedResults(emptyList(), 0, 0f)

        val selected = mutableListOf<ScoredChunk>()
        var totalTokens = 0
        var similaritySum = 0f

        for (chunk in chunks) {
            val chunkTokens = chunk.tokenCount.coerceAtLeast(0)
            val wouldExceed = totalTokens + chunkTokens > tokenBudget

            if (wouldExceed && selected.isNotEmpty()) {
                break
            }

            selected += chunk
            totalTokens += chunkTokens
            similaritySum += chunk.similarity

            if (totalTokens >= tokenBudget) {
                break
            }
        }

        return PackedResults(selected, totalTokens, similaritySum)
    }

    private data class PackedResults(
        val chunks: List<ScoredChunk>,
        val totalTokens: Int,
        val similaritySum: Float,
    )
}
