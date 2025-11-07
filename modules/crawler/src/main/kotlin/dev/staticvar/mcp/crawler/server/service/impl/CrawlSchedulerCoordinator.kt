package dev.staticvar.mcp.crawler.server.service.impl

import dev.inmo.krontab.doInfinity
import dev.staticvar.mcp.crawler.server.service.CrawlExecutionService
import dev.staticvar.mcp.crawler.server.service.CrawlSchedule
import dev.staticvar.mcp.crawler.server.service.CrawlScheduleService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CrawlSchedulerCoordinator(
    private val scheduleService: CrawlScheduleService,
    private val executionService: CrawlExecutionService,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : Closeable {

    private val logger = KotlinLogging.logger {}
    private val jobs = mutableMapOf<String, Job>()
    private val mutex = Mutex()

    suspend fun reload() {
        val schedules = scheduleService.list()
        mutex.withLock {
            val enabledSchedules = schedules.filter { it.enabled }
            val enabledIds = enabledSchedules.map { it.id }.toSet()

            val cancelled = jobs.filterKeys { it !in enabledIds }
            cancelled.forEach { (id, job) ->
                logger.debug { "Cancelling schedule $id" }
                job.cancel()
                jobs.remove(id)
            }

            enabledSchedules.forEach { schedule ->
                if (jobs.containsKey(schedule.id)) return@forEach

                logger.info { "Starting scheduler for ${schedule.id} (${schedule.cron})" }
                val job = scope.launch {
                    runSchedule(schedule)
                }
                jobs[schedule.id] = job
            }
        }
    }

    private suspend fun runSchedule(schedule: CrawlSchedule) {
        try {
            doInfinity(schedule.cron) {
                val result = executionService.trigger("schedule:${schedule.id}")
                if (result.accepted) {
                    logger.info { "Triggered scheduled crawl ${schedule.id} (job=${result.jobId})" }
                } else {
                    logger.debug { "Skipped scheduled crawl ${schedule.id}: ${result.message}" }
                }
            }
        } catch (cancellation: CancellationException) {
            logger.debug { "Scheduler ${schedule.id} cancelled" }
            throw cancellation
        } catch (unexpected: Exception) {
            logger.error(unexpected) { "Scheduler ${schedule.id} terminated unexpectedly" }
        } finally {
            mutex.withLock {
                jobs.remove(schedule.id)
            }
        }
    }

    override fun close() {
        scope.cancel()
    }
}
