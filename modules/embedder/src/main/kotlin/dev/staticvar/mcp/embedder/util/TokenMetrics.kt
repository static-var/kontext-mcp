package dev.staticvar.mcp.embedder.util

import dev.staticvar.mcp.embedder.tokenizer.TokenizedInput

/**
 * Helper utilities for tracking token usage within embedding batches.
 */
object TokenMetrics {

    /**
     * Calculates the total number of tokens consumed within [inputs].
     */
    fun totalTokens(inputs: List<TokenizedInput>): Int =
        inputs.sumOf { it.tokenCount }

    /**
     * Returns the maximum number of tokens used by a single input within [inputs].
     */
    fun maxTokens(inputs: List<TokenizedInput>): Int =
        inputs.maxOf { it.tokenCount }
}
