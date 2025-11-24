package dev.staticvar.mcp.embedder.util

import dev.staticvar.mcp.shared.config.EmbeddingConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readAvailable
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.notExists
import kotlin.io.path.pathString

private val logger = KotlinLogging.logger {}

data class ModelArtifacts(
    val modelFile: Path,
    val tokenizerFile: Path,
)

/**
 * Ensures model artifacts (ONNX weights + tokenizer) are available locally.
 * Supports both local filesystem paths and HuggingFace repository identifiers.
 */
class ModelDownloader(private val httpClient: HttpClient) {
    suspend fun ensureArtifacts(config: EmbeddingConfig): ModelArtifacts {
        val modelPath = Path.of(config.modelPath)
        if (modelPath.exists()) {
            return resolveFromLocal(modelPath, config.modelFilename)
        }

        // treat value as huggingface repo id (e.g., "BAAI/bge-large-en-v1.5")
        return downloadFromHuggingFace(
            repoId = config.modelPath,
            cacheDir = Path.of(config.modelCacheDir),
            modelFilename = config.modelFilename,
            quantized = config.quantized,
        )
    }

    private fun resolveFromLocal(
        path: Path,
        modelFilename: String?,
    ): ModelArtifacts {
        val modelFile =
            when {
                path.extension.equals("onnx", ignoreCase = true) -> path
                path.isDirectory() -> {
                    if (modelFilename != null) {
                        path.resolve(modelFilename)
                    } else {
                        // Try common names if no filename provided
                        val candidates = listOf("model.onnx", "onnx/model.onnx", "model_quantized.onnx")
                        candidates.map { path.resolve(it) }.firstOrNull { it.exists() }
                            ?: path.resolve("model.onnx") // Default fallback
                    }
                }
                else -> error("Unsupported model path: ${path.pathString}. Provide a directory or .onnx file.")
            }

        val tokenizerFile = modelFile.parent.resolve("tokenizer.json")

        require(modelFile.exists()) { "ONNX model not found at ${modelFile.pathString}" }
        require(tokenizerFile.exists()) { "Tokenizer file not found at ${tokenizerFile.pathString}" }

        return ModelArtifacts(modelFile, tokenizerFile)
    }

    private suspend fun downloadFromHuggingFace(
        repoId: String,
        cacheDir: Path,
        modelFilename: String?,
        quantized: Boolean,
    ): ModelArtifacts {
        val sanitized = sanitizeRepoId(repoId)
        val targetDir = cacheDir.resolve(sanitized)
        if (targetDir.notExists()) {
            targetDir.createDirectories()
        }

        // Determine which model file to look for
        val candidatePaths =
            when {
                modelFilename != null -> listOf(modelFilename)
                quantized -> listOf("model_quantized.onnx", "onnx/model_quantized.onnx", "model.onnx", "onnx/model.onnx")
                else -> listOf("onnx/model.onnx", "model.onnx")
            }

        val modelFile = resolveOrDownloadModel(repoId, targetDir, candidatePaths)
        val tokenizerFile = targetDir.resolve("tokenizer.json")

        if (tokenizerFile.notExists()) {
            downloadArtifact(
                url = huggingFaceUrl(repoId, "tokenizer.json"),
                target = tokenizerFile,
            )
        }

        return ModelArtifacts(modelFile, tokenizerFile)
    }

    private suspend fun resolveOrDownloadModel(
        repoId: String,
        targetDir: Path,
        candidates: List<String>,
    ): Path {
        // 1. Check if any candidate already exists locally
        for (candidate in candidates) {
            val localPath = targetDir.resolve(candidate.substringAfterLast("/"))
            if (localPath.exists()) return localPath
        }

        // 2. Try to download candidates in order
        for (candidate in candidates) {
            val targetName = candidate.substringAfterLast("/")
            val targetPath = targetDir.resolve(targetName)

            try {
                downloadArtifact(
                    url = huggingFaceUrl(repoId, candidate),
                    target = targetPath,
                )
                return targetPath
            } catch (e: IOException) {
                logger.warn { "Failed to download $candidate: ${e.message}. Trying next candidate..." }
                // Clean up partial download if any
                runCatching { Files.deleteIfExists(targetPath) }
            }
        }

        throw IOException("Failed to download any model file from $repoId. Tried: $candidates")
    }

    private suspend fun downloadArtifact(
        url: String,
        target: Path,
    ) {
        logger.info { "Downloading $url -> ${target.pathString}" }
        httpClient.prepareGet(url).execute { response ->
            if (response.status != HttpStatusCode.OK) {
                throw IOException("Failed to download $url (status=${response.status})")
            }

            target.parent?.createDirectories()
            val channel = response.bodyAsChannel()
            Files.newOutputStream(
                target,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            ).use { output ->
                val buffer = ByteArray(8 * 1024) // 8KB buffer
                while (true) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read == -1) break
                    if (read == 0) continue
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    private fun sanitizeRepoId(repoId: String): String = repoId.replace('/', '_').replace(':', '_')

    private fun huggingFaceUrl(
        repoId: String,
        artifact: String,
    ): String {
        val trimmed = artifact.removePrefix("/")
        return "https://huggingface.co/$repoId/resolve/main/$trimmed"
    }
}
