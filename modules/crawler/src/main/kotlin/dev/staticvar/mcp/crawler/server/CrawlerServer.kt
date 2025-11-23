package dev.staticvar.mcp.crawler.server

import com.typesafe.config.ConfigFactory
import dev.staticvar.mcp.crawler.server.bootstrap.CrawlerBootstrap
import dev.staticvar.mcp.crawler.server.bootstrap.CrawlerComponents
import dev.staticvar.mcp.crawler.server.config.CrawlerConfig
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.config.propertyOrNull
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.AttributeKey
import kotlinx.coroutines.runBlocking

/**
 * Entry point for launching the crawler web server.
 */
fun main(): Unit =
    runBlocking {
        val hoconConfig = HoconApplicationConfig(ConfigFactory.load())
        val crawlerConfig = CrawlerConfig.load(hoconConfig)

        val host = hoconConfig.propertyOrNull("ktor.deployment.host")?.getString() ?: "0.0.0.0"
        val port = hoconConfig.propertyOrNull("ktor.deployment.port")?.getString()?.toIntOrNull() ?: 8080

        val components = CrawlerBootstrap.initialize()

        try {
            embeddedServer(Netty, host = host, port = port) {
                installCrawlerModule(components, crawlerConfig)
            }.start(wait = true)
        } finally {
            components.close()
        }
    }

/**
 * Ktor deploy hook invoked by the engine.
 */
fun Application.main() {
    val crawlerConfig = CrawlerConfig.load(environment.config)
    val components = runBlocking { CrawlerBootstrap.initialize() }
    installCrawlerModule(components, crawlerConfig)
}

private val ComponentsKey = AttributeKey<CrawlerComponents>("crawler.components")

private fun Application.installCrawlerModule(
    components: CrawlerComponents,
    config: CrawlerConfig,
) {
    attributes.put(ComponentsKey, components)
    environment.monitor.subscribe(ApplicationStopping) {
        runCatching { attributes.remove(ComponentsKey) }
        components.close()
    }

    crawlerModule(
        config = config,
        services = components.services,
    )
}
