package dev.staticvar.mcp.server.evaluation

import dev.staticvar.mcp.embedder.service.EmbeddingService
import dev.staticvar.mcp.embedder.service.RerankerService
import dev.staticvar.mcp.embedder.service.model.EmbeddingBatchRequest
import dev.staticvar.mcp.embedder.service.model.EmbeddingResult
import dev.staticvar.mcp.indexer.database.DatabaseFactory
import dev.staticvar.mcp.indexer.repository.DocumentRepository
import dev.staticvar.mcp.indexer.repository.EmbeddingRepository
import dev.staticvar.mcp.indexer.repository.SourceUrlRepository
import dev.staticvar.mcp.indexer.repository.impl.DocumentRepositoryImpl
import dev.staticvar.mcp.indexer.repository.impl.EmbeddingRepositoryImpl
import dev.staticvar.mcp.indexer.repository.impl.SourceUrlRepositoryImpl
import dev.staticvar.mcp.server.search.SearchService
import dev.staticvar.mcp.shared.config.DatabaseConfig
import dev.staticvar.mcp.shared.config.RerankingConfig
import dev.staticvar.mcp.shared.config.RetrievalConfig
import dev.staticvar.mcp.shared.model.ContentType
import dev.staticvar.mcp.shared.model.Document
import dev.staticvar.mcp.shared.model.EmbeddedChunk
import dev.staticvar.mcp.shared.model.ParserType
import dev.staticvar.mcp.shared.model.SearchRequest
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock

@OptIn(kotlin.time.ExperimentalTime::class)
class SearchEvaluationIntegrationTest {
    @Test
    fun `evaluation dataset meets minimum quality bar`() =
        runBlocking {
            PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16")).use { container ->
                container.start()
                applyBootstrapSql(container)

                val dbConfig =
                    DatabaseConfig(
                        host = container.host,
                        port = container.firstMappedPort,
                        database = container.databaseName,
                        username = container.username,
                        password = container.password,
                        maxPoolSize = 4,
                    )
                DatabaseFactory.init(dbConfig)

                val expectations = EvaluationDataset.load()
                val embeddingService = DeterministicEmbeddingService()
                val sourceRepo: SourceUrlRepository = SourceUrlRepositoryImpl()
                val documentRepo: DocumentRepository = DocumentRepositoryImpl()
                val embeddingRepo: EmbeddingRepository = EmbeddingRepositoryImpl()

                seedDataset(expectations, sourceRepo, documentRepo, embeddingRepo, embeddingService)

                val searchService =
                    SearchService(
                        embeddingService = embeddingService,
                        embeddingRepository = embeddingRepo,
                        rerankerService = NoOpReranker(),
                        retrievalConfig = RetrievalConfig(),
                        rerankingConfig = RerankingConfig(enabled = false),
                    )

                val summary = evaluateDataset(expectations, searchService)
                println(
                    "Evaluation metrics -> P@5=${summary.precisionAt5.format()}, " +
                        "R@5=${summary.recallAt5.format()}, " +
                        "MRR=${summary.mrr.format()}",
                )

                assertTrue(summary.precisionAt5 >= 0.2, "Precision@5 too low: ${summary.precisionAt5}")
                assertTrue(summary.recallAt5 >= 0.2, "Recall@5 too low: ${summary.recallAt5}")
                assertTrue(summary.mrr >= 0.2, "MRR too low: ${summary.mrr}")
            }
        }

    private suspend fun seedDataset(
        expectations: List<QueryExpectation>,
        sourceRepo: SourceUrlRepository,
        documentRepo: DocumentRepository,
        embeddingRepo: EmbeddingRepository,
        embeddingService: EmbeddingService,
    ) {
        expectations.forEachIndexed { index, expectation ->
            val source =
                sourceRepo.insert(
                    url = "https://example.com/docs/$index",
                    parserType = ParserType.GENERIC_HTML,
                )

            val document =
                Document(
                    id = 0,
                    sourceUrl = source.url,
                    title = "Doc ${index + 1}: ${expectation.query}",
                    contentType = ContentType.GUIDE,
                    metadata =
                        mapOf(
                            "keywords" to expectation.expectedKeywords.joinToString(","),
                            "category" to if (expectation.query.contains("Compose", ignoreCase = true)) "compose" else "kotlin",
                        ),
                    lastIndexed = Clock.System.now(),
                )
            val documentId = documentRepo.insert(document)

            val chunkContent = chunkContent(expectation)
            val embedding = embeddingService.embed(EmbeddingBatchRequest(listOf(chunkContent))).first()
            val chunk =
                EmbeddedChunk(
                    id = 0,
                    documentId = documentId,
                    chunkIndex = 0,
                    content = chunkContent,
                    embedding = embedding.embedding,
                    metadata = mapOf("topic" to expectation.expectedKeywords.firstOrNull().orEmpty()),
                    tokenCount = chunkContent.split("\\s+".toRegex()).size * 3,
                )
            embeddingRepo.insertBatch(listOf(chunk))
        }
    }

    private suspend fun evaluateDataset(
        expectations: List<QueryExpectation>,
        searchService: SearchService,
    ): EvaluationSummary {
        var precisionAt5 = 0.0
        var recallAt5 = 0.0
        val reciprocalInputs = mutableListOf<List<Boolean>>()

        expectations.forEach { expectation ->
            val response =
                searchService.search(
                    SearchRequest(
                        query = expectation.query,
                        similarityThreshold = 0.2f,
                    ),
                )
            val flags = expectation.relevanceFlags(response.chunks)
            reciprocalInputs += flags
            precisionAt5 += RetrievalMetrics.precisionAtK(flags, 5)
            recallAt5 += RetrievalMetrics.recallAtK(flags, totalRelevant = 1, k = 5)
        }

        val total = expectations.size.toDouble()
        return EvaluationSummary(
            precisionAt5 = precisionAt5 / total,
            recallAt5 = recallAt5 / total,
            mrr = RetrievalMetrics.meanReciprocalRank(reciprocalInputs),
        )
    }

    private fun chunkContent(expectation: QueryExpectation): String =
        buildString {
            append(expectation.query)
            append(' ')
            repeat(3) {
                append(expectation.expectedKeywords.joinToString(" "))
                append(' ')
            }
        }.trim()

    private fun applyBootstrapSql(container: PostgreSQLContainer<*>) {
        val statements =
            listOf(
                "CREATE EXTENSION IF NOT EXISTS vector;",
                """
                DO $$ BEGIN
                    CREATE TYPE parser_type AS ENUM (
                        'ANDROID_DOCS',
                        'KOTLIN_LANG',
                        'MKDOCS',
                        'GITHUB',
                        'GENERIC_HTML'
                    );
                EXCEPTION
                    WHEN duplicate_object THEN null;
                END $$;
                """.trimIndent(),
                """
                DO $$ BEGIN
                    CREATE TYPE content_type AS ENUM (
                        'API_REFERENCE',
                        'GUIDE',
                        'TUTORIAL',
                        'RELEASE_NOTES',
                        'CODE_SAMPLE',
                        'OVERVIEW',
                        'ARTICLE',
                        'UNKNOWN'
                    );
                EXCEPTION
                    WHEN duplicate_object THEN null;
                END $$;
                """.trimIndent(),
                """
                DO $$ BEGIN
                    CREATE TYPE crawl_status AS ENUM (
                        'PENDING',
                        'IN_PROGRESS',
                        'SUCCESS',
                        'FAILED',
                        'DISABLED'
                    );
                EXCEPTION
                    WHEN duplicate_object THEN null;
                END $$;
                """.trimIndent(),
            )

        container.createConnection("").use { connection ->
            connection.createStatement().use { statement ->
                statements.forEach { sql -> statement.execute(sql) }
            }
        }
    }

    private data class EvaluationSummary(
        val precisionAt5: Double,
        val recallAt5: Double,
        val mrr: Double,
    )

    private fun Double.format(): String = "%.3f".format(this)

    private class DeterministicEmbeddingService(
        override val dimension: Int = 1024,
        override val maxTokens: Int = 1024,
    ) : EmbeddingService {
        override suspend fun embed(request: EmbeddingBatchRequest): List<EmbeddingResult> =
            request.texts.map { text ->
                val vector = encode(text)
                EmbeddingResult(
                    embedding = vector,
                    tokenCount = text.length.coerceAtMost(maxTokens),
                    truncated = false,
                )
            }

        private fun encode(text: String): FloatArray {
            val normalizedText =
                if (text.startsWith("Represent this sentence for searching relevant passages:")) {
                    text.substringAfter("Represent this sentence for searching relevant passages:").trim()
                } else {
                    text
                }
            val vector = FloatArray(dimension)
            val tokens =
                normalizedText.lowercase()
                    .split(Regex("\\W+"))
                    .filter { it.isNotBlank() }

            tokens.forEach { token ->
                val hash = abs(token.hashCode())
                val index = hash % dimension
                vector[index] += 1f
            }

            val sumSquares = vector.fold(0.0) { acc, value -> acc + value * value }
            val norm = sqrt(sumSquares).toFloat().coerceAtLeast(1e-6f)
            for (i in vector.indices) {
                vector[i] /= norm
            }
            return vector
        }
    }

    private class NoOpReranker : RerankerService {
        override suspend fun rerank(
            query: String,
            documents: List<String>,
        ): List<Int> {
            return documents.indices.toList()
        }
    }
}
