package dev.staticvar.mcp.embedder.service

import dev.staticvar.mcp.embedder.tokenizer.HuggingFaceEmbeddingTokenizer
import dev.staticvar.mcp.embedder.util.ModelArtifacts
import dev.staticvar.mcp.embedder.util.ModelDownloader
import dev.staticvar.mcp.shared.config.EmbeddingConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import java.io.Closeable

/**
 * Factory helpers for building ready-to-use [EmbeddingService] instances.
 */
object EmbeddingServiceFactory {

    suspend fun createBgeService(
        config: EmbeddingConfig,
        httpClient: HttpClient? = null
    ): EmbeddingService {
        val client = httpClient ?: defaultHttpClient()
        val downloader = ModelDownloader(client)
        val artifacts: ModelArtifacts = downloader.ensureArtifacts(config)

        val tokenizer = HuggingFaceEmbeddingTokenizer.create(artifacts.tokenizerFile, config.maxTokens)
        val modelRunner = OnnxEmbeddingModelRunner.create(artifacts.modelFile, config.dimension)

        if (httpClient == null) {
            client.close()
        }

        return BgeEmbeddingService(tokenizer, modelRunner, config)
    }

    private fun defaultHttpClient(): HttpClient = HttpClient(CIO)
}
