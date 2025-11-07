package dev.staticvar.mcp.embedder.util

import dev.staticvar.mcp.shared.config.EmbeddingConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readAvailable
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.DEFAULT_BUFFER_SIZE
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.notExists
import kotlin.io.path.pathString
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

data class ModelArtifacts(
    val modelFile: Path,
    val tokenizerFile: Path
)

/**
 * Ensures model artifacts (ONNX weights + tokenizer) are available locally.
 * Supports both local filesystem paths and HuggingFace repository identifiers.
 */
class ModelDownloader(private val httpClient: HttpClient) {

    suspend fun ensureArtifacts(config: EmbeddingConfig): ModelArtifacts {
        val modelPath = Path.of(config.modelPath)
        if (modelPath.exists()) {
            return resolveFromLocal(modelPath)
        }

        // treat value as huggingface repo id (e.g., "BAAI/bge-large-en-v1.5")
        return downloadFromHuggingFace(config.modelPath, Path.of(config.modelCacheDir))
    }

    private fun resolveFromLocal(path: Path): ModelArtifacts {
        val modelFile = when {
            path.extension.equals("onnx", ignoreCase = true) -> path
            path.isDirectory() -> path.resolve("model.onnx")
            else -> error("Unsupported model path: ${path.pathString}. Provide a directory or .onnx file.")
        }

        val tokenizerFile = modelFile.parent.resolve("tokenizer.json")

        require(modelFile.exists()) { "ONNX model not found at ${modelFile.pathString}" }
        require(tokenizerFile.exists()) { "Tokenizer file not found at ${tokenizerFile.pathString}" }

        return ModelArtifacts(modelFile, tokenizerFile)
    }

    private suspend fun downloadFromHuggingFace(repoId: String, cacheDir: Path): ModelArtifacts {
        val sanitized = sanitizeRepoId(repoId)
        val targetDir = cacheDir.resolve(sanitized)
        if (targetDir.notExists()) {
            targetDir.createDirectories()
        }

        val modelFile = targetDir.resolve("model.onnx")
        val tokenizerFile = targetDir.resolve("tokenizer.json")

        if (modelFile.notExists()) {
            downloadArtifact(
                url = huggingFaceUrl(repoId, "onnx/model.onnx"),
                target = modelFile
            )
        }

        if (tokenizerFile.notExists()) {
            downloadArtifact(
                url = huggingFaceUrl(repoId, "tokenizer.json"),
                target = tokenizerFile
            )
        }

        return ModelArtifacts(modelFile, tokenizerFile)
    }

    private suspend fun downloadArtifact(url: String, target: Path) {
        logger.info { "Downloading $url -> ${target.pathString}" }
        val response = httpClient.get(url)
        if (response.status != HttpStatusCode.OK) {
            throw IOException("Failed to download $url (status=${response.status})")
        }

        target.parent?.createDirectories()
        val channel = response.bodyAsChannel()
        Files.newOutputStream(
            target,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        ).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read == -1) break
                if (read == 0) continue
                output.write(buffer, 0, read)
            }
        }
    }

    private fun sanitizeRepoId(repoId: String): String =
        repoId.replace('/', '_').replace(':', '_')

    private fun huggingFaceUrl(repoId: String, artifact: String): String {
        val trimmed = artifact.removePrefix("/")
        return "https://huggingface.co/$repoId/resolve/main/$trimmed"
    }
}
