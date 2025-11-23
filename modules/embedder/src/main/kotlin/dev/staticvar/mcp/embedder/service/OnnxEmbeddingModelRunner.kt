package dev.staticvar.mcp.embedder.service

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtLoggingLevel
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtSession.SessionOptions
import ai.onnxruntime.TensorInfo
import dev.staticvar.mcp.embedder.tokenizer.TokenizedInput
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.math.sqrt

private val runnerLogger = KotlinLogging.logger {}

/**
 * ONNX Runtime based implementation for transformer sentence embedding models.
 */
class OnnxEmbeddingModelRunner private constructor(
    private val environment: OrtEnvironment,
    private val session: OrtSession,
    private val inputIdsName: String,
    private val attentionMaskName: String,
    private val tokenTypeIdsName: String?,
    override val dimension: Int,
    private val outputName: String,
) : EmbeddingModelRunner {
    override fun infer(batch: List<TokenizedInput>): List<FloatArray> {
        if (batch.isEmpty()) return emptyList()

        val batchSize = batch.size
        val sequenceLength = batch.maxOf { it.sequenceLength }

        val inputIds = Array(batchSize) { LongArray(sequenceLength) }
        val attentionMasks = Array(batchSize) { LongArray(sequenceLength) }
        val tokenTypeIds = Array(batchSize) { LongArray(sequenceLength) }

        batch.forEachIndexed { index, input ->
            inputIds[index].fillFrom(input.inputIds)
            attentionMasks[index].fillFrom(input.attentionMask)
            input.tokenTypeIds?.let { tokenTypeIds[index].fillFrom(it) }
        }

        val tensors = mutableMapOf<String, OnnxTensor>()
        try {
            tensors[inputIdsName] = OnnxTensor.createTensor(environment, inputIds)
            tensors[attentionMaskName] = OnnxTensor.createTensor(environment, attentionMasks)
            if (tokenTypeIdsName != null) {
                tensors[tokenTypeIdsName] = OnnxTensor.createTensor(environment, tokenTypeIds)
            }

            session.run(tensors, setOf(outputName)).use { outputs ->
                val tensor =
                    outputs.get(outputName)
                        .orElseThrow { IllegalStateException("Model output '$outputName' missing") }
                tensor.use {
                    if (it !is OnnxTensor) {
                        throw IllegalStateException("Model output '$outputName' is not a tensor")
                    }
                    return extractEmbeddings(
                        tensor = it,
                        batchSize = batchSize,
                        sequenceLength = sequenceLength,
                        masks = attentionMasks,
                    )
                }
            }
        } finally {
            tensors.values.forEach { it.close() }
        }
    }

    private fun extractEmbeddings(
        tensor: OnnxTensor,
        batchSize: Int,
        sequenceLength: Int,
        masks: Array<LongArray>,
    ): List<FloatArray> {
        val info =
            tensor.info as? TensorInfo
                ?: error("Unexpected output type ${tensor.info}")

        val rawShape = info.shape
        val buffer = tensor.floatBuffer
        val remaining = buffer.remaining()
        val (effectiveShape, hiddenSize) =
            when (rawShape.size) {
                2 -> {
                    val hidden = if (rawShape[1] > 0) rawShape[1].toInt() else dimension
                    require(hidden == dimension) {
                        "Expected embedding dimension $dimension, but model returned $hidden"
                    }
                    val batch = if (rawShape[0] > 0) rawShape[0].toInt() else batchSize
                    intArrayOf(batch, hidden) to hidden
                }

                3 -> {
                    val hidden = if (rawShape[2] > 0) rawShape[2].toInt() else dimension
                    require(hidden == dimension) {
                        "Expected embedding dimension $dimension, but model returned $hidden"
                    }
                    val batch = if (rawShape[0] > 0) rawShape[0].toInt() else batchSize
                    val seq = if (rawShape[1] > 0) rawShape[1].toInt() else sequenceLength
                    intArrayOf(batch, seq, hidden) to hidden
                }

                else -> error("Unsupported output shape ${rawShape.joinToString()}")
            }

        val embeddings = MutableList(batchSize) { FloatArray(dimension) }
        val floatArray = FloatArray(remaining)
        buffer.get(floatArray)

        when (effectiveShape.size) {
            2 -> fillFrom2D(floatArray, embeddings)
            3 -> fillFrom3D(floatArray, embeddings, masks, effectiveShape[1], hiddenSize)
        }

        embeddings.forEach { normalize(it) }
        return embeddings
    }

    private fun fillFrom2D(
        raw: FloatArray,
        target: MutableList<FloatArray>,
    ) {
        var offset = 0
        target.forEach { vector ->
            System.arraycopy(raw, offset, vector, 0, vector.size)
            offset += vector.size
        }
    }

    private fun fillFrom3D(
        raw: FloatArray,
        target: MutableList<FloatArray>,
        masks: Array<LongArray>,
        sequenceLength: Int,
        hiddenSize: Int,
    ) {
        var offset = 0
        target.forEachIndexed { batchIdx, vector ->
            val mask = masks.getOrNull(batchIdx) ?: LongArray(sequenceLength)
            val sum = FloatArray(hiddenSize)
            val cls = FloatArray(hiddenSize)
            var activeTokens = 0f

            repeat(sequenceLength) { tokenIdx ->
                val isActive = mask.getOrElse(tokenIdx) { 0L } > 0L
                repeat(hiddenSize) { hiddenIdx ->
                    val value = raw[offset++]
                    if (tokenIdx == 0) {
                        cls[hiddenIdx] = value
                    }
                    if (isActive) {
                        sum[hiddenIdx] += value
                    }
                }
                if (isActive) activeTokens += 1f
            }

            val divisor = if (activeTokens > 0f) activeTokens else 1f
            val source = if (activeTokens > 0f) sum else cls
            repeat(hiddenSize) { idx ->
                vector[idx] = source[idx] / divisor
            }
        }
    }

    override fun close() {
        runCatching { session.close() }
            .onFailure { runnerLogger.warn(it) { "Failed to close ONNX session" } }
    }

    private fun LongArray.fillFrom(source: LongArray) {
        val length = minOf(size, source.size)
        System.arraycopy(source, 0, this, 0, length)
    }

    private fun normalize(vector: FloatArray) {
        var sum = 0.0
        for (value in vector) {
            sum += value * value
        }
        val norm = sqrt(sum).toFloat()
        if (norm == 0f) return
        val inv = 1f / norm
        for (i in vector.indices) {
            vector[i] *= inv
        }
    }

    companion object {
        private val OUTPUT_CANDIDATES =
            listOf(
                "sentence_embedding",
                "pooled_output",
                "last_hidden_state",
                "embeddings",
            )

        fun create(
            modelPath: java.nio.file.Path,
            dimension: Int,
        ): OnnxEmbeddingModelRunner {
            val environment = OrtEnvironment.getEnvironment()
            SessionOptions().use { options ->
                options.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_WARNING)
                options.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
                options.setInterOpNumThreads(1)
                options.setOptimizationLevel(SessionOptions.OptLevel.BASIC_OPT)

                val session = environment.createSession(modelPath.toString(), options)

                val originalInputNames = session.inputNames.toList()
                val nameMap = originalInputNames.associateBy { it.lowercase() }

                fun resolveName(expected: String): String =
                    nameMap[expected] ?: throw IllegalStateException(
                        "Model input '$expected' not found. Available inputs: $originalInputNames",
                    )

                val inputIds = resolveName("input_ids")
                val attentionMask = resolveName("attention_mask")
                val tokenType = nameMap["token_type_ids"]

                val outputMap = session.outputNames.associateBy { it.lowercase() }
                val outputName =
                    OUTPUT_CANDIDATES.firstNotNullOfOrNull { candidate ->
                        outputMap[candidate]
                    } ?: session.outputNames.firstOrNull()
                        ?: throw IllegalStateException("ONNX model exposes no outputs")

                return OnnxEmbeddingModelRunner(
                    environment = environment,
                    session = session,
                    inputIdsName = inputIds,
                    attentionMaskName = attentionMask,
                    tokenTypeIdsName = tokenType,
                    dimension = dimension,
                    outputName = outputName,
                )
            }
        }
    }
}
