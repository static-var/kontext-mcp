package dev.staticvar.mcp.crawler.server.service.impl

import dev.staticvar.mcp.crawler.server.persistence.CrawlSchedulesTable
import dev.staticvar.mcp.crawler.server.service.CrawlSchedule
import dev.staticvar.mcp.crawler.server.service.CrawlScheduleService
import dev.staticvar.mcp.crawler.server.service.UpsertScheduleRequest
import dev.staticvar.mcp.indexer.database.dbQuery
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class DatabaseCrawlScheduleService : CrawlScheduleService {

    override suspend fun list(): List<CrawlSchedule> = dbQuery {
        CrawlSchedulesTable
            .selectAll()
            .orderBy(CrawlSchedulesTable.id to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun upsert(request: UpsertScheduleRequest): CrawlSchedule {
        val cron = request.cron.trim()
        validateCronExpression(cron)

        return dbQuery {
            val identifier = request.id?.toIntOrNull()

            val idValue = if (identifier == null) {
                CrawlSchedulesTable.insert {
                    it[CrawlSchedulesTable.cronExpression] = cron
                    it[CrawlSchedulesTable.description] = request.description
                    it[CrawlSchedulesTable.enabled] = true
                }[CrawlSchedulesTable.id].value
            } else {
                val updated = CrawlSchedulesTable.update({ CrawlSchedulesTable.id eq identifier }) {
                    it[CrawlSchedulesTable.cronExpression] = cron
                    it[CrawlSchedulesTable.description] = request.description
                }

                if (updated == 0) {
                    CrawlSchedulesTable.insert {
                        it[CrawlSchedulesTable.cronExpression] = cron
                        it[CrawlSchedulesTable.description] = request.description
                        it[CrawlSchedulesTable.enabled] = true
                    }[CrawlSchedulesTable.id].value
                } else {
                    identifier
                }
            }

            CrawlSchedulesTable
                .selectAll()
                .where { CrawlSchedulesTable.id eq idValue }
                .single()
                .toModel()
        }
    }

    override suspend fun delete(id: String): Boolean {
        val identifier = id.toIntOrNull() ?: return false
        return dbQuery {
            CrawlSchedulesTable.deleteWhere { CrawlSchedulesTable.id eq identifier } > 0
        }
    }

    private fun validateCronExpression(cron: String) {
        val fields = cron.split(Regex("\\s+"))
        require(fields.size in 5..6) { "Cron expression must contain 5 or 6 fields" }
    }

    private fun ResultRow.toModel(): CrawlSchedule = CrawlSchedule(
        id = this[CrawlSchedulesTable.id].value.toString(),
        cron = this[CrawlSchedulesTable.cronExpression],
        enabled = this[CrawlSchedulesTable.enabled],
        description = this[CrawlSchedulesTable.description]
    )
}
