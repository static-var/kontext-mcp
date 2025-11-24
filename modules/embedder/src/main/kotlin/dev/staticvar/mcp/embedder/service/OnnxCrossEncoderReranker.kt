package dev.staticvar.mcp.embedder.service

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import dev.staticvar.mcp.embedder.tokenizer.HuggingFaceEmbeddingTokenizer
import dev.staticvar.mcp.embedder.util.ModelDownloader
import dev.staticvar.mcp.shared.config.EmbeddingConfig
import dev.staticvar.mcp.shared.config.RerankingConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.LongBuffer

private val logger = KotlinLogging.logger {}

class OnnxCrossEncoderReranker(
    private val config: RerankingConfig,
    private val downloader: ModelDownloader,
) : RerankerService {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var tokenizer: HuggingFaceEmbeddingTokenizer? = null

    // BGE reranker typically uses 512 max length
    private val maxTokens = 512

    suspend fun init() {
        if (!config.enabled) return

        logger.info { "Initializing Reranker with model: ${config.modelPath}" }

        // Reuse EmbeddingConfig structure for ModelDownloader compatibility
        // This is a bit of a hack, ideally ModelDownloader should take a generic config or params
        val embeddingConfig =
            EmbeddingConfig(
                modelPath = config.modelPath,
                modelCacheDir = config.modelCacheDir,
                modelFilename = config.modelFilename,
                quantized = config.quantized,
                // Unused for download
                dimension = 0,
                // Unused
                batchSize = 0,
                maxTokens = maxTokens,
            )

        val artifacts = downloader.ensureArtifacts(embeddingConfig)

        val sessionOptions = OrtSession.SessionOptions()
        // Limit threads to avoid over-subscription in container
        // Ideally this should be configurable or derived from cgroup limits, but Java's availableProcessors
        // can be misleading in containers depending on JVM version and flags.
        // Let's be conservative and use 2 threads (matching our docker compose limit)
        sessionOptions.setIntraOpNumThreads(2)
        sessionOptions.setInterOpNumThreads(1)
        sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)

        session = env.createSession(artifacts.modelFile.toString(), sessionOptions)
        tokenizer = HuggingFaceEmbeddingTokenizer.create(artifacts.tokenizerFile, maxTokens)

        // Warm-up
        logger.info { "Warming up Reranker..." }
        try {
            val dummyQuery = "warm up query"
            val dummyDoc = "warm up document"
            rerank(dummyQuery, listOf(dummyDoc))
            logger.info { "Reranker warm-up complete" }
        } catch (e: Exception) {
            logger.warn(e) { "Reranker warm-up failed" }
        }

        logger.info { "Reranker initialized successfully" }
    }

    override suspend fun rerank(
        query: String,
        documents: List<String>,
    ): List<Int> =
        withContext(Dispatchers.IO) {
            if (session == null || tokenizer == null) {
                logger.warn { "Reranker not initialized or disabled. Returning original order." }
                return@withContext documents.indices.toList()
            }

            if (documents.isEmpty()) return@withContext emptyList()

            // Batch processing
            val batchSize = documents.size
            val seqLength = maxTokens

            // Flatten arrays for batch tensor
            val inputIdsFlat = LongArray(batchSize * seqLength)
            val attentionMaskFlat = LongArray(batchSize * seqLength)
            val tokenTypeIdsFlat = LongArray(batchSize * seqLength)

            documents.forEachIndexed { i, doc ->
                val tokenized = tokenizer!!.tokenizePair(query, doc)
                // Ensure we don't overflow if tokenized length differs (though it shouldn't with padToMaxLength)
                val length = minOf(tokenized.inputIds.size, seqLength)

                System.arraycopy(tokenized.inputIds, 0, inputIdsFlat, i * seqLength, length)
                System.arraycopy(tokenized.attentionMask, 0, attentionMaskFlat, i * seqLength, length)

                val typeIds = tokenized.tokenTypeIds ?: LongArray(length) { 0 }
                System.arraycopy(typeIds, 0, tokenTypeIdsFlat, i * seqLength, length)
            }

            val shape = longArrayOf(batchSize.toLong(), seqLength.toLong())

            val inputTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIdsFlat), shape)
            val maskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMaskFlat), shape)

            val inputs = mutableMapOf<String, OnnxTensor>()
            val modelInputs = session!!.inputNames

            // Match model input names (case-insensitive)
            val inputIdsName = modelInputs.firstOrNull { it.equals("input_ids", ignoreCase = true) }
            val attentionMaskName = modelInputs.firstOrNull { it.equals("attention_mask", ignoreCase = true) }
            val tokenTypeIdsName = modelInputs.firstOrNull { it.equals("token_type_ids", ignoreCase = true) }

            if (inputIdsName != null) inputs[inputIdsName] = inputTensor
            if (attentionMaskName != null) inputs[attentionMaskName] = maskTensor

            var typeTensor: OnnxTensor? = null
            if (tokenTypeIdsName != null) {
                typeTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypeIdsFlat), shape)
                inputs[tokenTypeIdsName] = typeTensor
            }

            val results = session!!.run(inputs)
            val logits = results[0].value as Array<FloatArray> // [batchSize, 1]

            val scores =
                logits.mapIndexed { index, row ->
                    val score = sigmoid(row[0])
                    index to score
                }

            results.close()
            inputTensor.close()
            maskTensor.close()
            typeTensor?.close()

            scores.sortedByDescending { it.second }.map { it.first }
        }

    private fun sigmoid(x: Float): Float {
        return (1.0 / (1.0 + Math.exp(-x.toDouble()))).toFloat()
    }

    override fun close() {
        session?.close()
        tokenizer?.close()
    }
}
