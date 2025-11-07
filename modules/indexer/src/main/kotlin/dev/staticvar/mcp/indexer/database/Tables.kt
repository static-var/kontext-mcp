package dev.staticvar.mcp.indexer.database

import dev.staticvar.mcp.indexer.database.column.PgEnum
import dev.staticvar.mcp.indexer.database.column.jsonbMap
import dev.staticvar.mcp.indexer.database.column.vector
import dev.staticvar.mcp.shared.model.ContentType
import dev.staticvar.mcp.shared.model.CrawlStatus
import dev.staticvar.mcp.shared.model.ParserType
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * Exposed table definitions matching Flyway schema.
 * Using custom enums to match PostgreSQL enum types.
 */
object SourceUrlsTable : IntIdTable("source_urls") {
    val url = text("url").uniqueIndex()
    val parserType = customEnumeration(
        "parser_type",
        "parser_type",
        { value -> ParserType.valueOf(value as String) },
        { PgEnum("parser_type", it) }
    )
    val enabled = bool("enabled").default(true)
    val lastCrawled = timestamp("last_crawled").nullable()
    val etag = text("etag").nullable()
    val lastModified = text("last_modified").nullable()
    val status = customEnumeration(
        "status",
        "crawl_status",
        { value -> CrawlStatus.valueOf(value as String) },
        { PgEnum("crawl_status", it) }
    ).default(CrawlStatus.PENDING)
    val errorMessage = text("error_message").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object DocumentsTable : IntIdTable("documents") {
    val sourceUrl = text("source_url")
        .references(SourceUrlsTable.url, onDelete = ReferenceOption.CASCADE)
    val title = text("title")
    val contentType = customEnumeration(
        "content_type",
        "content_type",
        { value -> ContentType.valueOf(value as String) },
        { PgEnum("content_type", it) }
    )
    val metadata = jsonbMap("metadata")
    val lastIndexed = timestamp("last_indexed")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object EmbeddingsTable : IntIdTable("embeddings") {
    val documentId = integer("document_id")
        .references(DocumentsTable.id, onDelete = ReferenceOption.CASCADE)
    val chunkIndex = integer("chunk_index")
    val content = text("content")
    val embedding = vector("embedding", dimension = 1024)
    val metadata = jsonbMap("metadata")
    val tokenCount = integer("token_count")
    val createdAt = timestamp("created_at")

    init {
        uniqueIndex(documentId, chunkIndex)
    }
}
