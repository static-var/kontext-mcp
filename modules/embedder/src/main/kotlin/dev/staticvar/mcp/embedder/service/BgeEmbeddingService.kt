package dev.staticvar.mcp.embedder.service

import dev.staticvar.mcp.embedder.service.model.EmbeddingBatchRequest
import dev.staticvar.mcp.embedder.service.model.EmbeddingResult
import dev.staticvar.mcp.embedder.tokenizer.EmbeddingTokenizer
import dev.staticvar.mcp.embedder.util.TokenMetrics
import dev.staticvar.mcp.shared.config.EmbeddingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.oshai.kotlinlogging.KotlinLogging

private val serviceLogger = KotlinLogging.logger {}

/**
 * Embedding service tailored for BAAI's BGE-large-en-v1.5 model.
 */
class BgeEmbeddingService(
    private val tokenizer: EmbeddingTokenizer,
    private val modelRunner: EmbeddingModelRunner,
    private val config: EmbeddingConfig
) : EmbeddingService {

    override val dimension: Int = modelRunner.dimension
    override val maxTokens: Int = config.maxTokens

    override suspend fun embed(request: EmbeddingBatchRequest): List<EmbeddingResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<EmbeddingResult>()
        val batches = request.texts.chunked(config.batchSize)

        for (batch in batches) {
            val tokenized = batch.map(tokenizer::tokenize)
            val embeddings = modelRunner.infer(tokenized)

            if (embeddings.size != tokenized.size) {
                error("Model returned ${embeddings.size} embeddings for batch of size ${tokenized.size}")
            }

            embeddings.zip(tokenized).forEach { (vector, tokens) ->
                results += EmbeddingResult(
                    embedding = vector,
                    tokenCount = tokens.tokenCount,
                    truncated = tokens.truncated
                )
            }

            serviceLogger.debug {
                "Embedded batch size=${batch.size}, totalTokens=${TokenMetrics.totalTokens(tokenized)}, " +
                    "maxTokens=${TokenMetrics.maxTokens(tokenized)}"
            }
        }

        results
    }

    override fun close() {
        runCatching { tokenizer.close() }
            .onFailure { serviceLogger.warn(it) { "Failed to close tokenizer" } }
        runCatching { modelRunner.close() }
            .onFailure { serviceLogger.warn(it) { "Failed to close ONNX runner" } }
    }
}
