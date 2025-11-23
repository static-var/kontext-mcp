package dev.staticvar.mcp.shared.config

/**
 * Application configuration loaded from application.conf and environment variables.
 * All knobs for tuning system behavior without redeployment.
 */
data class AppConfig(
    val database: DatabaseConfig,
    val embedding: EmbeddingConfig,
    val chunking: ChunkingConfig,
    val retrieval: RetrievalConfig,
    val reranking: RerankingConfig,
    val crawler: CrawlerConfig
)

data class RerankingConfig(
    val enabled: Boolean = false,
    val modelPath: String = "BAAI/bge-reranker-base",
    val modelCacheDir: String = "./models",
    val modelFilename: String? = null,
    val quantized: Boolean = false
)

data class DatabaseConfig(
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String,
    val maxPoolSize: Int = 10
) {
    val jdbcUrl: String
        get() = "jdbc:postgresql://$host:$port/$database"
}

data class EmbeddingConfig(
    val modelPath: String,
    val modelCacheDir: String = "./models",
    val modelFilename: String? = null,
    val quantized: Boolean = false,
    val dimension: Int = 1024,
    val batchSize: Int = 32,
    val maxTokens: Int = 512
)

data class ChunkingConfig(
    val targetTokens: Int = 512,
    val overlapTokens: Int = 50,
    val maxTokens: Int = 1024,
    val preserveCodeBlocks: Boolean = true,
    val preserveSectionHierarchy: Boolean = true
)

data class RetrievalConfig(
    val defaultTokenBudget: Int = 2000,
    val maxTokenBudget: Int = 8000,
    val defaultSimilarityThreshold: Float = 0.7f,
    val topKCandidates: Int = 30,
    val enableReranking: Boolean = false
)

data class CrawlerConfig(
    val maxConcurrentRequests: Int = 5,
    val requestDelayMs: Long = 1000,
    val userAgent: String = "AndroidKotlinMCP/0.1.0",
    val timeout: Long = 30000,
    val retryAttempts: Int = 3
)
