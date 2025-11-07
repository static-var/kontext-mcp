package dev.staticvar.mcp.embedder

import dev.staticvar.mcp.embedder.util.ModelDownloader
import dev.staticvar.mcp.shared.config.EmbeddingConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ModelDownloaderTest {

    @Test
    fun `uses local filesystem model`() = runTest {
        val tempDir = createTempDirectory()
        val modelFile = createTempFile(directory = tempDir, suffix = ".onnx")
        modelFile.writeText("onnx")
        val tokenizerFile = tempDir.resolve("tokenizer.json")
        tokenizerFile.writeText("""{"dummy": true}""")

        val config = EmbeddingConfig(
            modelPath = modelFile.toString(),
            modelCacheDir = tempDir.toString(),
            dimension = 2,
            batchSize = 1,
            maxTokens = 4
        )

        val downloader = ModelDownloader(HttpClient(MockEngine { error("network should not be invoked for local paths") }))

        val artifacts = downloader.ensureArtifacts(config)

        assertEquals(modelFile, artifacts.modelFile)
        assertEquals(tokenizerFile, artifacts.tokenizerFile)
    }

    @Test
    fun `downloads artifacts when missing`() = runTest {
        val tempDir = createTempDirectory()
        val repoId = "Test/Repo"
        val modelBytes = "onnx-model"
        val tokenizerBytes = """{"tokenizer": "test"}"""

        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/onnx/model.onnx") -> respond(
                    content = modelBytes,
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Length", modelBytes.length.toString())
                )

                request.url.encodedPath.endsWith("/tokenizer.json") -> respond(
                    content = tokenizerBytes,
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Length", tokenizerBytes.length.toString())
                )

                else -> respond("Not found", HttpStatusCode.NotFound)
            }
        }

        val client = HttpClient(engine)
        val downloader = ModelDownloader(client)

        val config = EmbeddingConfig(
            modelPath = repoId,
            modelCacheDir = tempDir.toString(),
            dimension = 2,
            batchSize = 1,
            maxTokens = 4
        )

        val artifacts = downloader.ensureArtifacts(config)

        assertTrue(artifacts.modelFile.toFile().exists())
        assertTrue(artifacts.tokenizerFile.toFile().exists())
        assertEquals(modelBytes, artifacts.modelFile.toFile().readText())
        assertEquals(tokenizerBytes, artifacts.tokenizerFile.toFile().readText())
    }
}
