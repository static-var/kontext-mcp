package dev.staticvar.mcp.crawler.server.service

import dev.staticvar.mcp.indexer.repository.DocumentRepository
import dev.staticvar.mcp.indexer.repository.EmbeddingRepository
import dev.staticvar.mcp.indexer.repository.SourceUrlRepository
import dev.staticvar.mcp.shared.config.AppConfig
import dev.staticvar.mcp.shared.model.CrawlStatus
import kotlinx.serialization.Serializable

interface SystemStatsService {
    suspend fun getStats(): SystemStats
}

@Serializable
data class SystemStats(
    // Model configuration
    val embeddingModel: String,
    val embeddingDimension: Int,
    val embeddingQuantized: Boolean,
    val maxTokensPerChunk: Int,
    val rerankingEnabled: Boolean,
    val rerankingModel: String?,
    val rerankingQuantized: Boolean?,
    // Retrieval settings
    val defaultTokenBudget: Int,
    val maxTokenBudget: Int,
    val defaultSimilarityThreshold: Float,
    val topKCandidates: Int,
    // Chunking settings
    val targetChunkTokens: Int,
    val overlapTokens: Int,
    // Document stats
    val totalSources: Long,
    val successfulSources: Long,
    val pendingSources: Long,
    val failedSources: Long,
    val totalDocuments: Long,
    val totalEmbeddings: Long,
    // Estimated size (assuming 4 bytes per float32 dimension)
    val estimatedEmbeddingSizeMB: Double,
)

class SystemStatsServiceImpl(
    private val appConfig: AppConfig,
    private val documentRepository: DocumentRepository,
    private val embeddingRepository: EmbeddingRepository,
    private val sourceUrlRepository: SourceUrlRepository? = null,
) : SystemStatsService {
    override suspend fun getStats(): SystemStats {
        val totalDocuments = documentRepository.count()
        val totalEmbeddings = embeddingRepository.count()

        // Calculate estimated embedding storage size
        // Each embedding: dimension * 4 bytes (float32)
        val bytesPerEmbedding = appConfig.embedding.dimension * 4L
        val totalBytes = totalEmbeddings * bytesPerEmbedding
        val estimatedSizeMB = totalBytes / (1024.0 * 1024.0)

        // Get source URL stats if available
        val sourceStats =
            sourceUrlRepository?.let { repo ->
                val sources = repo.findAll()
                SourceStats(
                    total = sources.size.toLong(),
                    successful = sources.count { it.status == CrawlStatus.SUCCESS }.toLong(),
                    pending = sources.count { it.status == CrawlStatus.PENDING }.toLong(),
                    failed = sources.count { it.status == CrawlStatus.FAILED }.toLong(),
                )
            } ?: SourceStats(0, 0, 0, 0)

        return SystemStats(
            embeddingModel = appConfig.embedding.modelPath,
            embeddingDimension = appConfig.embedding.dimension,
            embeddingQuantized = appConfig.embedding.quantized,
            maxTokensPerChunk = appConfig.embedding.maxTokens,
            rerankingEnabled = appConfig.reranking.enabled,
            rerankingModel = appConfig.reranking.modelPath.takeIf { appConfig.reranking.enabled },
            rerankingQuantized = appConfig.reranking.quantized.takeIf { appConfig.reranking.enabled },
            defaultTokenBudget = appConfig.retrieval.defaultTokenBudget,
            maxTokenBudget = appConfig.retrieval.maxTokenBudget,
            defaultSimilarityThreshold = appConfig.retrieval.defaultSimilarityThreshold,
            topKCandidates = appConfig.retrieval.topKCandidates,
            targetChunkTokens = appConfig.chunking.targetTokens,
            overlapTokens = appConfig.chunking.overlapTokens,
            totalSources = sourceStats.total,
            successfulSources = sourceStats.successful,
            pendingSources = sourceStats.pending,
            failedSources = sourceStats.failed,
            totalDocuments = totalDocuments,
            totalEmbeddings = totalEmbeddings,
            estimatedEmbeddingSizeMB = estimatedSizeMB,
        )
    }

    private data class SourceStats(
        val total: Long,
        val successful: Long,
        val pending: Long,
        val failed: Long,
    )
}
