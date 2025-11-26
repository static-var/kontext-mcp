package dev.staticvar.mcp.embedder

import dev.staticvar.mcp.embedder.service.BgeEmbeddingService
import dev.staticvar.mcp.embedder.service.EmbeddingModelRunner
import dev.staticvar.mcp.embedder.service.model.EmbeddingBatchRequest
import dev.staticvar.mcp.embedder.tokenizer.EmbeddingTokenizer
import dev.staticvar.mcp.embedder.tokenizer.TokenizedInput
import dev.staticvar.mcp.shared.config.EmbeddingConfig
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BgeEmbeddingServiceTest {
    @Test
    fun `embeds batches and preserves token metadata`() =
        runBlocking {
            val config =
                EmbeddingConfig(
                    modelPath = "unused",
                    modelCacheDir = "unused",
                    dimension = 3,
                    batchSize = 2,
                    maxTokens = 4,
                )

            val tokenizer = FakeTokenizer(maxTokens = config.maxTokens)
            val runner = FakeModelRunner(dimension = config.dimension)

            BgeEmbeddingService(tokenizer, runner, config).use { service ->
                val request =
                    EmbeddingBatchRequest(
                        listOf("alpha beta", "gamma", "delta epsilon zeta", "eta theta iota kappa lambda"),
                    )
                val results = service.embed(request)

                assertEquals(4, results.size)

                // Ensure batching took place (two batches of size 2)
                assertEquals(2, runner.processedBatches.size)
                assertEquals(2, runner.processedBatches.first().size)

                val first = results[0]
                assertEquals(2, first.tokenCount)
                assertFalse(first.truncated)

                val truncated = results.last()
                assertTrue(truncated.truncated)
                assertEquals(config.maxTokens, truncated.tokenCount)

                results.forEach { result ->
                    assertEquals(config.dimension, result.embedding.size)
                }
            }
        }
}

private class FakeTokenizer(private val maxTokens: Int) : EmbeddingTokenizer {
    override fun tokenize(text: String): TokenizedInput {
        val tokens = text.split(" ")
        val tokenCount = tokens.size.coerceAtMost(maxTokens)
        val inputIds = LongArray(maxTokens) { (it + 1).toLong() }
        val attention = LongArray(maxTokens) { if (it < tokenCount) 1L else 0L }
        return TokenizedInput(
            inputIds = inputIds,
            attentionMask = attention,
            tokenTypeIds = null,
            tokenCount = tokenCount,
            truncated = tokens.size > maxTokens,
        )
    }

    override fun tokenizePair(
        text1: String,
        text2: String,
    ): TokenizedInput = tokenize("$text1 $text2")

    override fun close() {}
}

private class FakeModelRunner(override val dimension: Int) : EmbeddingModelRunner {
    val processedBatches = mutableListOf<List<TokenizedInput>>()

    override fun infer(batch: List<TokenizedInput>): List<FloatArray> {
        processedBatches += batch
        return batch.mapIndexed { batchIndex, _ ->
            FloatArray(dimension) { component -> (batchIndex + component + 1).toFloat() }
        }
    }

    override fun close() {}
}
