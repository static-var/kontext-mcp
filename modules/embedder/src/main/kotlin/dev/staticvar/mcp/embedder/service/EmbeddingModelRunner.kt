package dev.staticvar.mcp.embedder.service

import dev.staticvar.mcp.embedder.tokenizer.TokenizedInput

/**
 * Abstraction around a backend that converts tokenized inputs into embeddings.
 */
interface EmbeddingModelRunner : AutoCloseable {

    val dimension: Int

    fun infer(batch: List<TokenizedInput>): List<FloatArray>

    override fun close() {
        // default no-op
    }
}
