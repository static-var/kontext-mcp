package dev.staticvar.mcp.embedder.tokenizer

/**
 * Abstraction for tokenizing raw text into transformer-friendly tensors.
 */
interface EmbeddingTokenizer : AutoCloseable {
    /**
     * Tokenizes [text] into model-ready ids and masks.
     */
    fun tokenize(text: String): TokenizedInput

    override fun close() {
        // default no-op
    }
}
