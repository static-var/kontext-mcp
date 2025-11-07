package dev.staticvar.mcp.embedder

import dev.staticvar.mcp.embedder.tokenizer.TokenizedInput
import dev.staticvar.mcp.embedder.util.TokenMetrics
import kotlin.test.Test
import kotlin.test.assertEquals

class TokenMetricsTest {

    @Test
    fun `computes totals`() {
        val inputs = listOf(
            tokenized(tokenCount = 2),
            tokenized(tokenCount = 4),
            tokenized(tokenCount = 1)
        )

        assertEquals(7, TokenMetrics.totalTokens(inputs))
        assertEquals(4, TokenMetrics.maxTokens(inputs))
    }

    private fun tokenized(tokenCount: Int): TokenizedInput {
        val maxLen = 5
        val mask = LongArray(maxLen) { if (it < tokenCount) 1L else 0L }
        return TokenizedInput(
            inputIds = LongArray(maxLen),
            attentionMask = mask,
            tokenTypeIds = null,
            tokenCount = tokenCount,
            truncated = false
        )
    }
}
