package dev.staticvar.mcp.crawler.server.persistence

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.datetime.timestamp

object CrawlSchedulesTable : IntIdTable("crawl_schedules") {
    val cronExpression = text("cron_expression")
    val description = text("description").nullable()
    val enabled = bool("enabled").default(true)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}
