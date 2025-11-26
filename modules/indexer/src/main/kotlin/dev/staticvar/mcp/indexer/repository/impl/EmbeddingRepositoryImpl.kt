package dev.staticvar.mcp.indexer.repository.impl

import dev.staticvar.mcp.indexer.database.EmbeddingsTable
import dev.staticvar.mcp.indexer.database.dbQuery
import dev.staticvar.mcp.indexer.repository.EmbeddingRepository
import dev.staticvar.mcp.indexer.repository.ScoredChunk
import dev.staticvar.mcp.shared.model.EmbeddedChunk
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.FloatColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.statements.api.JdbcPreparedStatementApi
import org.postgresql.util.PGobject

class EmbeddingRepositoryImpl : EmbeddingRepository {
    override suspend fun insertBatch(chunks: List<EmbeddedChunk>) {
        if (chunks.isEmpty()) return

        dbQuery {
            val sql =
                """
                INSERT INTO embeddings (document_id, chunk_index, content, embedding, metadata, token_count)
                VALUES (?, ?, ?, ?, ?::jsonb, ?)
                ON CONFLICT (document_id, chunk_index) DO UPDATE
                SET content = EXCLUDED.content,
                    embedding = EXCLUDED.embedding,
                    metadata = EXCLUDED.metadata,
                    token_count = EXCLUDED.token_count
                """.trimIndent()

            val statement = connection.prepareStatement(sql, false)
            try {
                chunks.forEach { chunk ->
                    bindChunk(statement, chunk)
                    statement.addBatch()
                }
                statement.executeBatch()
            } finally {
                statement.closeIfPossible()
            }
        }
    }

    override suspend fun search(
        queryEmbedding: FloatArray,
        limit: Int,
        similarityThreshold: Float,
        filters: Map<String, String>?,
    ): List<ScoredChunk> =
        dbQuery {
            val (filterClause, filterParams) = buildMetadataFilter(filters)

            val sql =
                """
                SELECT e.content,
                       d.source_url,
                       1 - (e.embedding <=> ?::vector) AS similarity,
                       e.metadata,
                       e.token_count
                FROM embeddings e
                JOIN documents d ON e.document_id = d.id
                WHERE 1 - (e.embedding <=> ?::vector) >= ?
                $filterClause
                ORDER BY e.embedding <=> ?::vector
                LIMIT ?
                """.trimIndent()

            val results = mutableListOf<ScoredChunk>()
            val statement = connection.prepareStatement(sql, false)
            try {
                var parameterIndex = 1

                statement.set(parameterIndex++, queryEmbedding, EmbeddingsTable.embedding.columnType)
                statement.set(parameterIndex++, queryEmbedding, EmbeddingsTable.embedding.columnType)
                statement.set(parameterIndex++, similarityThreshold, FloatColumnType())

                filterParams.forEach { param ->
                    statement.set(parameterIndex++, param, VarCharColumnType())
                }

                statement.set(parameterIndex++, queryEmbedding, EmbeddingsTable.embedding.columnType)
                statement.set(parameterIndex, limit, IntegerColumnType())

                val jdbcResult = statement.executeQuery()
                try {
                    val resultSet = jdbcResult.result
                    while (resultSet.next()) {
                        val content = resultSet.getString("content")
                        val sourceUrl = resultSet.getString("source_url")
                        val similarity = resultSet.getDouble("similarity").toFloat()
                        val metadata = parseMetadata(resultSet.getObject("metadata"))
                        val tokenCount = resultSet.getInt("token_count")

                        results +=
                            ScoredChunk(
                                content = content,
                                sourceUrl = sourceUrl,
                                similarity = similarity,
                                metadata = metadata,
                                tokenCount = tokenCount,
                            )
                    }
                } finally {
                    jdbcResult.close()
                }
            } finally {
                statement.closeIfPossible()
            }

            results
        }

    override suspend fun deleteByDocumentId(documentId: Int) {
        dbQuery {
            EmbeddingsTable.deleteWhere { EmbeddingsTable.documentId eq documentId }
        }
    }

    override suspend fun count(): Long =
        dbQuery {
            EmbeddingsTable.selectAll().count()
        }

    private fun bindChunk(
        statement: JdbcPreparedStatementApi,
        chunk: EmbeddedChunk,
    ) {
        statement.set(1, chunk.documentId, EmbeddingsTable.documentId.columnType)
        statement.set(2, chunk.chunkIndex, EmbeddingsTable.chunkIndex.columnType)
        statement.set(3, chunk.content, EmbeddingsTable.content.columnType)
        statement.set(4, chunk.embedding, EmbeddingsTable.embedding.columnType)
        statement.set(5, encodeMetadata(chunk.metadata), VarCharColumnType())
        statement.set(6, chunk.tokenCount, EmbeddingsTable.tokenCount.columnType)
    }

    private fun buildMetadataFilter(filters: Map<String, String>?): Pair<String, List<String>> {
        if (filters.isNullOrEmpty()) return "" to emptyList()

        val conditions = StringBuilder()
        val params = mutableListOf<String>()

        filters.forEach { (key, value) ->
            conditions.appendLine("AND e.metadata ->> ? = ?")
            params += key
            params += value
        }

        return conditions.toString() to params
    }

    private fun parseMetadata(raw: Any?): Map<String, String> =
        when (raw) {
            is PGobject -> json.decodeFromString(serializer, raw.value ?: "{}")
            is String -> json.decodeFromString(serializer, raw)
            else -> emptyMap()
        }

    private companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = MapSerializer(String.serializer(), String.serializer())

        private fun encodeMetadata(metadata: Map<String, String>): String = json.encodeToString(serializer, metadata)
    }
}
