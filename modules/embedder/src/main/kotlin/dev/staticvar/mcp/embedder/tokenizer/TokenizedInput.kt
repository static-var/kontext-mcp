package dev.staticvar.mcp.embedder.tokenizer

/**
 * Tokenized representation of an input string compatible with transformer-based models.
 */
data class TokenizedInput(
    val inputIds: LongArray,
    val attentionMask: LongArray,
    val tokenTypeIds: LongArray?,
    val tokenCount: Int,
    val truncated: Boolean,
) {
    val sequenceLength: Int
        get() = inputIds.size
}
