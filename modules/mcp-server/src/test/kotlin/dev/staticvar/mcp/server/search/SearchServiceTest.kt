package dev.staticvar.mcp.server.search

import dev.staticvar.mcp.embedder.service.EmbeddingService
import dev.staticvar.mcp.embedder.service.RerankerService
import dev.staticvar.mcp.embedder.service.model.EmbeddingBatchRequest
import dev.staticvar.mcp.embedder.service.model.EmbeddingResult
import dev.staticvar.mcp.indexer.repository.EmbeddingRepository
import dev.staticvar.mcp.indexer.repository.ScoredChunk
import dev.staticvar.mcp.shared.config.RerankingConfig
import dev.staticvar.mcp.shared.config.RetrievalConfig
import dev.staticvar.mcp.shared.model.EmbeddedChunk
import dev.staticvar.mcp.shared.model.SearchRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SearchServiceTest {

    @Test
    fun `respects token budget when packing chunks`() = runTest {
        val embedding = floatArrayOf(0.1f, 0.2f, 0.3f)
        val embeddingService = FakeEmbeddingService(embedding)
        val repository = FakeEmbeddingRepository(
            listOf(
                ScoredChunk(
                    content = "Chunk 1 content",
                    sourceUrl = "https://example.com/1",
                    similarity = 0.92f,
                    metadata = mapOf("api_level" to "34"),
                    tokenCount = 80
                ),
                ScoredChunk(
                    content = "Chunk 2 content",
                    sourceUrl = "https://example.com/2",
                    similarity = 0.74f,
                    metadata = emptyMap(),
                    tokenCount = 50
                )
            )
        )

        val service = SearchService(
            embeddingService = embeddingService,
            embeddingRepository = repository,
            rerankerService = NoOpReranker(),
            retrievalConfig = RetrievalConfig(defaultTokenBudget = 120, maxTokenBudget = 200, topKCandidates = 5),
            rerankingConfig = RerankingConfig(enabled = false)
        )

        val response = service.search(
            SearchRequest(
                query = "Jetpack Compose lists",
                tokenBudget = 100
            )
        )

        assertEquals(1, response.chunks.size, "Only one chunk should fit within the token budget.")
        assertEquals(80, response.totalTokens)
        assertEquals(0.92f, response.confidence)
        assertEquals("https://example.com/1", response.chunks.first().source)
        assertEquals(0.7f, repository.lastSimilarityThreshold, "Repository should receive default similarity threshold.")
        assertTrue(embeddingService.requests.isNotEmpty(), "Embedding service should be invoked.")
    }

    @Test
    fun `falls back to default similarity threshold for invalid request value`() = runTest {
        val embeddingService = FakeEmbeddingService(floatArrayOf(0.0f))
        val repository = FakeEmbeddingRepository(emptyList())
        val config = RetrievalConfig(defaultSimilarityThreshold = 0.45f, topKCandidates = 5)
        val service = SearchService(
            embeddingService,
            repository,
            NoOpReranker(),
            config,
            RerankingConfig(enabled = false)
        )

        val response = service.search(
            SearchRequest(
                query = "Coroutines",
                similarityThreshold = -1f
            )
        )

        assertEquals(0f, response.confidence)
        assertEquals(0, response.totalTokens)
        assertEquals(0.45f, repository.lastSimilarityThreshold)
    }

    private class FakeEmbeddingService(
        private val embedding: FloatArray,
        override val dimension: Int = embedding.size,
        override val maxTokens: Int = 512
    ) : EmbeddingService {
        val requests = mutableListOf<EmbeddingBatchRequest>()

        override suspend fun embed(request: EmbeddingBatchRequest): List<EmbeddingResult> {
            requests += request
            return request.texts.map { EmbeddingResult(embedding, tokenCount = 32, truncated = false) }
        }
    }

    private class FakeEmbeddingRepository(
        private val results: List<ScoredChunk>
    ) : EmbeddingRepository {
        var lastSimilarityThreshold: Float = 0f
            private set

        override suspend fun insertBatch(chunks: List<EmbeddedChunk>) {
            error("Not expected in tests")
        }

        override suspend fun search(
            queryEmbedding: FloatArray,
            limit: Int,
            similarityThreshold: Float,
            filters: Map<String, String>?
        ): List<ScoredChunk> {
            lastSimilarityThreshold = similarityThreshold
            return results
        }

        override suspend fun deleteByDocumentId(documentId: Int) {
            error("Not expected in tests")
        }
    }

    private class NoOpReranker : RerankerService {
        override suspend fun rerank(query: String, documents: List<String>): List<Int> {
            return documents.indices.toList()
        }
    }
}
