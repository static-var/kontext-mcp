package dev.staticvar.mcp.embedder.service

import dev.staticvar.mcp.embedder.util.ModelDownloader
import dev.staticvar.mcp.shared.config.RerankingConfig
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class OnnxCrossEncoderRerankerTest {

    @Test
    fun `rerank returns original order when disabled`() = runTest {
        val config = RerankingConfig(enabled = false)
        val downloader = mockk<ModelDownloader>()
        
        val reranker = OnnxCrossEncoderReranker(config, downloader)
        reranker.init()
        
        val documents = listOf("doc1", "doc2", "doc3")
        val indices = reranker.rerank("query", documents)
        
        assertEquals(listOf(0, 1, 2), indices)
    }
    
    // Note: Testing actual ONNX inference requires a real model file and is better suited for integration tests.
    // Here we verify the logic around enabling/disabling and handling empty inputs.
    
    @Test
    fun `rerank returns empty list for empty documents`() = runTest {
        val config = RerankingConfig(enabled = true)
        val downloader = mockk<ModelDownloader>()
        
        // Mock ensureArtifacts to avoid actual download if init tries it
        // However, init() will fail if we don't mock the return. 
        // Since we can't easily mock the ONNX session creation without more abstraction,
        // we'll stick to testing the disabled state or error handling here.
        // For a proper unit test of logic, we'd need to abstract the OnnxEnvironment/Session.
        
        val reranker = OnnxCrossEncoderReranker(config, downloader)
        // We skip init() to simulate "failed to init" or just check pre-checks
        
        val indices = reranker.rerank("query", emptyList())
        assertEquals(emptyList(), indices)
    }
}
