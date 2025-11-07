@file:OptIn(kotlin.time.ExperimentalTime::class)

package dev.staticvar.mcp.indexer.repository

import dev.staticvar.mcp.indexer.database.DatabaseFactory
import dev.staticvar.mcp.indexer.database.DocumentsTable
import dev.staticvar.mcp.indexer.database.EmbeddingsTable
import dev.staticvar.mcp.indexer.database.SourceUrlsTable
import dev.staticvar.mcp.indexer.database.dbQuery
import dev.staticvar.mcp.indexer.repository.DocumentRepository
import dev.staticvar.mcp.indexer.repository.EmbeddingRepository
import dev.staticvar.mcp.indexer.repository.SourceUrlRepository
import dev.staticvar.mcp.indexer.repository.impl.DocumentRepositoryImpl
import dev.staticvar.mcp.indexer.repository.impl.EmbeddingRepositoryImpl
import dev.staticvar.mcp.indexer.repository.impl.SourceUrlRepositoryImpl
import dev.staticvar.mcp.shared.config.DatabaseConfig
import dev.staticvar.mcp.shared.model.ContentType
import dev.staticvar.mcp.shared.model.CrawlStatus
import dev.staticvar.mcp.shared.model.Document
import dev.staticvar.mcp.shared.model.EmbeddedChunk
import dev.staticvar.mcp.shared.model.ParserType
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IndexerRepositoryTest {

    private val container = PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16")).apply {
        withDatabaseName("mcp_db")
        withUsername("mcp_user")
        withPassword("mcp_pass")
    }

    private lateinit var sourceUrlRepository: SourceUrlRepository
    private lateinit var documentRepository: DocumentRepository
    private lateinit var embeddingRepository: EmbeddingRepository

    @BeforeAll
    fun setUp() {
        container.start()
        applyBootstrapSql()

        val config = DatabaseConfig(
            host = container.host,
            port = container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
            database = container.databaseName,
            username = container.username,
            password = container.password,
            maxPoolSize = 4
        )

        DatabaseFactory.init(config)

        sourceUrlRepository = SourceUrlRepositoryImpl()
        documentRepository = DocumentRepositoryImpl()
        embeddingRepository = EmbeddingRepositoryImpl()
    }

    @AfterAll
    fun tearDown() {
        container.stop()
    }

    @BeforeEach
    fun cleanDatabase() {
        runBlocking {
            dbQuery {
                EmbeddingsTable.deleteAll()
                DocumentsTable.deleteAll()
                SourceUrlsTable.deleteAll()
            }
        }
    }

    @Test
    fun `source url repository lifecycle`() = runBlocking {
        val inserted = sourceUrlRepository.insert("https://example.com/guide", ParserType.ANDROID_DOCS)
        assertEquals(CrawlStatus.PENDING, inserted.status)

        val pending = sourceUrlRepository.findPending()
        assertTrue(pending.any { it.id == inserted.id })

        val all = sourceUrlRepository.findAll()
        assertEquals(1, all.size)
        assertEquals(inserted.id, all.first().id)

        sourceUrlRepository.updateStatus(inserted.id, CrawlStatus.IN_PROGRESS, null)
        sourceUrlRepository.updateCrawlMetadata(inserted.id, etag = "etag-123", lastModified = "Thu, 01 Jan 1970 00:00:00 GMT")
        sourceUrlRepository.markCrawled(inserted.id)

        val fetched = sourceUrlRepository.findById(inserted.id)
        assertNotNull(fetched)
        assertEquals("etag-123", fetched.etag)
        assertEquals("Thu, 01 Jan 1970 00:00:00 GMT", fetched.lastModified)
        assertEquals(CrawlStatus.IN_PROGRESS, fetched.status)
        assertNotNull(fetched.lastCrawled)
        assertTrue(sourceUrlRepository.findPending().isEmpty())

        assertTrue(sourceUrlRepository.delete(inserted.id))
        assertTrue(sourceUrlRepository.findAll().isEmpty())
    }

    @Test
    fun `document repository operations`() = runBlocking {
        val sourceUrl = sourceUrlRepository.insert("https://example.com/doc", ParserType.ANDROID_DOCS)

        val now = Clock.System.now()
        val document = Document(
            id = 0,
            sourceUrl = sourceUrl.url,
            title = "Compose Basics",
            contentType = ContentType.GUIDE,
            metadata = mapOf("api_level" to "34"),
            lastIndexed = now
        )

        val documentId = documentRepository.insert(document)
        val fetched = documentRepository.findById(documentId)

        assertNotNull(fetched)
        assertEquals(document.title, fetched.title)
        assertEquals(document.metadata, fetched.metadata)

        val bySourceUrl = documentRepository.findBySourceUrl(sourceUrl.url)
        assertEquals(1, bySourceUrl.size)

        documentRepository.deleteBySourceUrl(sourceUrl.url)
        assertTrue(documentRepository.findBySourceUrl(sourceUrl.url).isEmpty())
    }

    @Test
    fun `embedding repository insert search and delete`() = runBlocking {
        val sourceUrl = sourceUrlRepository.insert("https://example.com/api", ParserType.KOTLIN_LANG)
        val documentId = documentRepository.insert(
            Document(
                id = 0,
                sourceUrl = sourceUrl.url,
                title = "Coroutines Guide",
                contentType = ContentType.GUIDE,
                metadata = mapOf("topic" to "coroutines"),
                lastIndexed = Clock.System.now()
            )
        )

        val embeddingVector = FloatArray(1024) { index -> (index + 1) / 1024f }
        val chunk = EmbeddedChunk(
            id = 0,
            documentId = documentId,
            chunkIndex = 0,
            content = "Kotlin coroutines allow asynchronous programming using suspend functions.",
            embedding = embeddingVector,
            metadata = mapOf("topic" to "coroutines"),
            tokenCount = 120
        )

        embeddingRepository.insertBatch(listOf(chunk))

        val searchResults = embeddingRepository.search(
            queryEmbedding = embeddingVector,
            limit = 5,
            similarityThreshold = 0.5f,
            filters = mapOf("topic" to "coroutines")
        )

        assertEquals(1, searchResults.size)
        val result = searchResults.first()
        assertEquals(chunk.content, result.content)
        assertTrue(result.similarity >= 0.99f)
        assertEquals(chunk.metadata, result.metadata)

        embeddingRepository.deleteByDocumentId(documentId)
        assertTrue(
            embeddingRepository.search(
                queryEmbedding = embeddingVector,
                limit = 5,
                similarityThreshold = 0.0f
            ).isEmpty()
        )
    }

    private fun applyBootstrapSql() {
        val statements = listOf(
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
            """.trimIndent()
        )

        container.createConnection("").use { connection ->
            connection.createStatement().use { statement ->
                statements.forEach { sql ->
                    statement.execute(sql)
                }
            }
        }
    }
}
