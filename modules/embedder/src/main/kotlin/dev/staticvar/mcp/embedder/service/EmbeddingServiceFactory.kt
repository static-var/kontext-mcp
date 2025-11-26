package dev.staticvar.mcp.embedder.service

import dev.staticvar.mcp.embedder.service.model.EmbeddingBatchRequest
import dev.staticvar.mcp.embedder.tokenizer.HuggingFaceEmbeddingTokenizer
import dev.staticvar.mcp.embedder.util.ModelArtifacts
import dev.staticvar.mcp.embedder.util.ModelDownloader
import dev.staticvar.mcp.shared.config.EmbeddingConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

/**
 * Factory helpers for building ready-to-use [EmbeddingService] instances.
 */
object EmbeddingServiceFactory {
    suspend fun createBgeService(
        config: EmbeddingConfig,
        httpClient: HttpClient? = null,
    ): EmbeddingService {
        val client = httpClient ?: defaultHttpClient()
        val downloader = ModelDownloader(client)
        val artifacts: ModelArtifacts = downloader.ensureArtifacts(config)

        val tokenizer = HuggingFaceEmbeddingTokenizer.create(artifacts.tokenizerFile, config.maxTokens)
        val modelRunner = OnnxEmbeddingModelRunner.create(artifacts.modelFile, config.dimension)

        if (httpClient == null) {
            client.close()
        }

        val service = BgeEmbeddingService(tokenizer, modelRunner, config)

        // Warm-up
        try {
            service.embed(EmbeddingBatchRequest(listOf("warm up")))
        } catch (e: Exception) {
            // Log warning but don't fail startup
            KotlinLogging.logger {}.warn(e) { "Embedding service warm-up failed" }
        }

        return service
    }

    private fun defaultHttpClient(): HttpClient = HttpClient(CIO)
}
