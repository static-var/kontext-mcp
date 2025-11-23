package dev.staticvar.mcp.indexer.repository.impl

import dev.staticvar.mcp.indexer.database.DocumentsTable
import dev.staticvar.mcp.indexer.database.dbQuery
import dev.staticvar.mcp.indexer.repository.DocumentRepository
import dev.staticvar.mcp.shared.model.Document
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class DocumentRepositoryImpl : DocumentRepository {
    override suspend fun insert(document: Document): Int =
        dbQuery {
            DocumentsTable.insert {
                it[DocumentsTable.sourceUrl] = document.sourceUrl
                it[DocumentsTable.title] = document.title
                it[DocumentsTable.contentType] = document.contentType
                it[DocumentsTable.metadata] = document.metadata
                it[DocumentsTable.lastIndexed] = document.lastIndexed
            }[DocumentsTable.id].value
        }

    override suspend fun findById(id: Int): Document? =
        dbQuery {
            DocumentsTable
                .selectAll()
                .where { DocumentsTable.id eq id }
                .singleOrNull()
                ?.toDocument()
        }

    override suspend fun findBySourceUrl(sourceUrl: String): List<Document> =
        dbQuery {
            DocumentsTable
                .selectAll()
                .where { DocumentsTable.sourceUrl eq sourceUrl }
                .orderBy(DocumentsTable.lastIndexed to SortOrder.DESC)
                .map { it.toDocument() }
        }

    override suspend fun deleteBySourceUrl(sourceUrl: String) {
        dbQuery {
            DocumentsTable.deleteWhere { DocumentsTable.sourceUrl eq sourceUrl }
        }
    }

    private fun ResultRow.toDocument(): Document =
        Document(
            id = this[DocumentsTable.id].value,
            sourceUrl = this[DocumentsTable.sourceUrl],
            title = this[DocumentsTable.title],
            contentType = this[DocumentsTable.contentType],
            metadata = this[DocumentsTable.metadata],
            lastIndexed = this[DocumentsTable.lastIndexed],
        )
}
