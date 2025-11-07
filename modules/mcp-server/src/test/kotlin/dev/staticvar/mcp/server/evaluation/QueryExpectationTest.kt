package dev.staticvar.mcp.server.evaluation

import dev.staticvar.mcp.shared.model.RetrievedChunk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueryExpectationTest {
    @Test
    fun `relevance flags require similarity and keywords`() {
        val expectation = QueryExpectation(
            query = "Test query",
            expectedKeywords = listOf("compose", "state"),
            minSimilarity = 0.7f
        )
        val chunks = listOf(
            RetrievedChunk(
                content = "Compose state basics",
                source = "https://example.com",
                similarity = 0.8f,
                metadata = emptyMap()
            ),
            RetrievedChunk(
                content = "Irrelevant text",
                source = "https://example.com/2",
                similarity = 0.9f,
                metadata = emptyMap()
            )
        )

        val flags = expectation.relevanceFlags(chunks)
        assertEquals(listOf(true, false), flags)
        assertTrue(expectation.matches(chunks.first()))
        assertFalse(expectation.matches(chunks.last()))
    }
}
