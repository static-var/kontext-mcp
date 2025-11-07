package dev.staticvar.mcp.crawler.server.service.impl

import dev.staticvar.mcp.crawler.server.service.CrawlSchedule
import dev.staticvar.mcp.crawler.server.service.CrawlScheduleService
import dev.staticvar.mcp.crawler.server.service.UpsertScheduleRequest

class CoordinatedCrawlScheduleService(
    private val delegate: CrawlScheduleService,
    private val coordinator: CrawlSchedulerCoordinator
) : CrawlScheduleService {

    override suspend fun list(): List<CrawlSchedule> = delegate.list()

    override suspend fun upsert(request: UpsertScheduleRequest): CrawlSchedule {
        val schedule = delegate.upsert(request)
        coordinator.reload()
        return schedule
    }

    override suspend fun delete(id: String): Boolean {
        val removed = delegate.delete(id)
        if (removed) {
            coordinator.reload()
        }
        return removed
    }
}
