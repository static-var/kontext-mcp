package dev.staticvar.mcp.embedder.tokenizer

import ai.djl.huggingface.tokenizers.Encoding
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import java.io.IOException
import java.nio.file.Path

/**
 * [EmbeddingTokenizer] backed by HuggingFace's tokenizer implementation (via DJL).
 */
class HuggingFaceEmbeddingTokenizer private constructor(
    private val tokenizer: HuggingFaceTokenizer,
    private val maxTokens: Int,
) : EmbeddingTokenizer {
    override fun tokenize(text: String): TokenizedInput {
        val encoding = tokenizer.encode(text)
        val ids = encoding.ids()
        val attentionMask = encoding.attentionMask()
        val typeIds = encoding.typeIds()

        return TokenizedInput(
            inputIds = ids,
            attentionMask = attentionMask,
            tokenTypeIds = if (typeIds.allZero()) null else typeIds,
            tokenCount = attentionMask.count { it == 1L },
            truncated = encoding.exceedMaxLength(),
        )
    }

    override fun close() {
        tokenizer.close()
    }

    companion object {
        /**
         * Builds a tokenizer using the provided [tokenizerPath].
         */
        @Throws(IOException::class)
        fun create(
            tokenizerPath: Path,
            maxTokens: Int,
        ): HuggingFaceEmbeddingTokenizer {
            val tokenizer =
                HuggingFaceTokenizer.builder()
                    .optTokenizerPath(tokenizerPath)
                    .optAddSpecialTokens(true)
                    .optPadding(true)
                    .optPadToMaxLength()
                    .optTruncation(true)
                    .optMaxLength(maxTokens)
                    .build()

            return HuggingFaceEmbeddingTokenizer(tokenizer, maxTokens)
        }
    }
}

private fun Encoding.ids(): LongArray = getIds()

private fun Encoding.attentionMask(): LongArray = getAttentionMask()

private fun Encoding.typeIds(): LongArray = getTypeIds()

private fun LongArray.allZero(): Boolean = all { it == 0L }
