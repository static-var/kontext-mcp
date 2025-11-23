@file:OptIn(kotlin.time.ExperimentalTime::class)

package dev.staticvar.mcp.indexer.repository.impl

import dev.staticvar.mcp.indexer.database.SourceUrlsTable
import dev.staticvar.mcp.indexer.database.dbQuery
import dev.staticvar.mcp.indexer.repository.SourceUrlRepository
import dev.staticvar.mcp.shared.model.CrawlStatus
import dev.staticvar.mcp.shared.model.ParserType
import dev.staticvar.mcp.shared.model.SourceUrl
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock

class SourceUrlRepositoryImpl : SourceUrlRepository {
    override suspend fun insert(
        url: String,
        parserType: ParserType,
    ): SourceUrl =
        dbQuery {
            val id = SourceUrlsTable.insertReturningId(url, parserType)
            SourceUrlsTable
                .selectAll()
                .where { SourceUrlsTable.id eq id }
                .single()
                .toSourceUrl()
        }

    override suspend fun findById(id: Int): SourceUrl? =
        dbQuery {
            SourceUrlsTable
                .selectAll()
                .where { SourceUrlsTable.id eq id }
                .singleOrNull()
                ?.toSourceUrl()
        }

    override suspend fun findByUrl(url: String): SourceUrl? =
        dbQuery {
            SourceUrlsTable
                .selectAll()
                .where { SourceUrlsTable.url eq url }
                .singleOrNull()
                ?.toSourceUrl()
        }

    override suspend fun findAll(): List<SourceUrl> =
        dbQuery {
            SourceUrlsTable
                .selectAll()
                .orderBy(
                    SourceUrlsTable.id to SortOrder.ASC,
                )
                .map { it.toSourceUrl() }
        }

    override suspend fun findEnabled(): List<SourceUrl> =
        dbQuery {
            SourceUrlsTable
                .selectAll()
                .where { SourceUrlsTable.enabled eq true }
                .orderBy(
                    SourceUrlsTable.lastCrawled to SortOrder.ASC,
                    SourceUrlsTable.id to SortOrder.ASC,
                )
                .map { it.toSourceUrl() }
        }

    override suspend fun findPending(limit: Int): List<SourceUrl> =
        dbQuery {
            SourceUrlsTable
                .selectAll()
                .where {
                    (SourceUrlsTable.status eq CrawlStatus.PENDING) and
                        (SourceUrlsTable.enabled eq true)
                }
                .orderBy(
                    SourceUrlsTable.lastCrawled to SortOrder.ASC,
                    SourceUrlsTable.id to SortOrder.ASC,
                )
                .limit(limit)
                .map { it.toSourceUrl() }
        }

    override suspend fun updateStatus(
        id: Int,
        status: CrawlStatus,
        errorMessage: String?,
    ) {
        dbQuery {
            SourceUrlsTable.update({ SourceUrlsTable.id eq id }) {
                it[SourceUrlsTable.status] = status
                it[SourceUrlsTable.errorMessage] = errorMessage
            }
        }
    }

    override suspend fun updateCrawlMetadata(
        id: Int,
        etag: String?,
        lastModified: String?,
    ) {
        dbQuery {
            SourceUrlsTable.update({ SourceUrlsTable.id eq id }) {
                it[SourceUrlsTable.etag] = etag
                it[SourceUrlsTable.lastModified] = lastModified
            }
        }
    }

    override suspend fun markCrawled(id: Int) {
        dbQuery {
            val now = Clock.System.now()
            SourceUrlsTable.update({ SourceUrlsTable.id eq id }) {
                it[SourceUrlsTable.lastCrawled] = now
            }
        }
    }

    override suspend fun delete(id: Int): Boolean =
        dbQuery {
            SourceUrlsTable.deleteWhere { SourceUrlsTable.id eq id } > 0
        }

    private fun ResultRow.toSourceUrl(): SourceUrl =
        SourceUrl(
            id = this[SourceUrlsTable.id].value,
            url = this[SourceUrlsTable.url],
            parserType = this[SourceUrlsTable.parserType],
            enabled = this[SourceUrlsTable.enabled],
            lastCrawled = this[SourceUrlsTable.lastCrawled],
            etag = this[SourceUrlsTable.etag],
            lastModified = this[SourceUrlsTable.lastModified],
            status = this[SourceUrlsTable.status],
            errorMessage = this[SourceUrlsTable.errorMessage],
        )

    private fun SourceUrlsTable.insertReturningId(
        url: String,
        parserType: ParserType,
    ): Int {
        val insertStatement =
            insert {
                it[SourceUrlsTable.url] = url
                it[SourceUrlsTable.parserType] = parserType
                it[SourceUrlsTable.enabled] = true
                it[SourceUrlsTable.status] = CrawlStatus.PENDING
            }
        return insertStatement[SourceUrlsTable.id].value
    }
}
