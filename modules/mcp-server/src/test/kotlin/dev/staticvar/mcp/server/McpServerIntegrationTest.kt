package dev.staticvar.mcp.server

import dev.staticvar.mcp.embedder.service.EmbeddingService
import dev.staticvar.mcp.embedder.service.RerankerService
import dev.staticvar.mcp.embedder.service.model.EmbeddingBatchRequest
import dev.staticvar.mcp.embedder.service.model.EmbeddingResult
import dev.staticvar.mcp.indexer.repository.EmbeddingRepository
import dev.staticvar.mcp.indexer.repository.ScoredChunk
import dev.staticvar.mcp.server.search.SearchService
import dev.staticvar.mcp.shared.config.RerankingConfig
import dev.staticvar.mcp.shared.config.RetrievalConfig
import dev.staticvar.mcp.shared.model.EmbeddedChunk
import dev.staticvar.mcp.shared.model.SearchResponse
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class McpServerIntegrationTest {
    @Test
    fun `search_docs tool returns structured response`() =
        runTest {
            val scoredChunks =
                listOf(
                    ScoredChunk(
                        content = "Paging overview",
                        sourceUrl = "https://developer.android.com/topic/libraries/architecture/paging",
                        similarity = 0.93f,
                        metadata = mapOf("api_level" to "34"),
                        tokenCount = 120,
                    ),
                )

            val searchService = searchServiceWith(scoredChunks)
            val tool = locateSearchDocsTool(searchService)

            val result =
                tool.handler.invoke(
                    CallToolRequest(
                        name = "search_docs",
                        arguments = buildJsonObject { put("query", JsonPrimitive("Paging library overview")) },
                    ),
                )

            val textContent = result.content.first() as TextContent
            assertTrue(textContent.text?.contains("https://developer.android.com") == true)

            val structured = result.structuredContent ?: error("structured content missing")
            val response = McpJson.decodeFromJsonElement(SearchResponse.serializer(), structured)

            assertEquals(1, response.chunks.size)
            assertEquals("https://developer.android.com/topic/libraries/architecture/paging", response.chunks.first().source)
            assertEquals(120, response.totalTokens)
        }

    @Test
    fun `search_docs tool surfaces argument validation errors`() =
        runTest {
            val searchService = searchServiceWith(emptyList())
            val tool = locateSearchDocsTool(searchService)

            val result =
                tool.handler.invoke(
                    CallToolRequest(
                        name = "search_docs",
                        arguments = JsonObject(emptyMap()),
                    ),
                )

            assertTrue(result.isError == true)
            val textContent = result.content.first() as TextContent
            assertNotNull(textContent.text)
            assertTrue(textContent.text!!.contains("Invalid arguments"))
        }

    private fun locateSearchDocsTool(searchService: SearchService): RegisteredTool {
        val server = buildServer(searchService)
        return server.tools["search_docs"] ?: error("search_docs tool was not registered")
    }

    private fun searchServiceWith(results: List<ScoredChunk>): SearchService {
        val embeddingService =
            object : EmbeddingService {
                override val dimension: Int = 3
                override val maxTokens: Int = 512

                override suspend fun embed(request: EmbeddingBatchRequest): List<EmbeddingResult> =
                    request.texts.map { EmbeddingResult(FloatArray(dimension) { 0.1f }, tokenCount = 16, truncated = false) }
            }

        val repository =
            object : EmbeddingRepository {
                override suspend fun insertBatch(chunks: List<EmbeddedChunk>) = error("not expected")

                override suspend fun search(
                    queryEmbedding: FloatArray,
                    limit: Int,
                    similarityThreshold: Float,
                    filters: Map<String, String>?,
                ): List<ScoredChunk> = results

                override suspend fun deleteByDocumentId(documentId: Int) = error("not expected")
            }

        return SearchService(
            embeddingService = embeddingService,
            embeddingRepository = repository,
            rerankerService = NoOpReranker(),
            retrievalConfig = RetrievalConfig(defaultTokenBudget = 400, maxTokenBudget = 800, topKCandidates = 5),
            rerankingConfig = RerankingConfig(enabled = false),
        )
    }

    private class NoOpReranker : RerankerService {
        override suspend fun rerank(
            query: String,
            documents: List<String>,
        ): List<Int> {
            return documents.indices.toList()
        }
    }
}
