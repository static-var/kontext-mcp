package dev.staticvar.mcp.crawler.server.service.impl

import dev.staticvar.mcp.crawler.server.service.AddSourceUrlRequest
import dev.staticvar.mcp.crawler.server.service.SourceUrlRecord
import dev.staticvar.mcp.crawler.server.service.SourceUrlService
import dev.staticvar.mcp.indexer.repository.SourceUrlRepository
import dev.staticvar.mcp.parser.registry.ParserRegistry
import dev.staticvar.mcp.shared.model.ParserType
import io.github.oshai.kotlinlogging.KotlinLogging

class DatabaseSourceUrlService(
    private val repository: SourceUrlRepository,
    private val parserRegistry: ParserRegistry,
) : SourceUrlService {
    override suspend fun list(): List<SourceUrlRecord> =
        repository
            .findAll()
            .map { it.toRecord() }

    override suspend fun add(request: AddSourceUrlRequest): SourceUrlRecord {
        println("DEBUG: Adding URL: ${request.url}")
        val normalizedUrl = request.url.trim()
        require(normalizedUrl.isNotEmpty()) { "URL must not be blank" }

        println("DEBUG: Checking if URL exists: $normalizedUrl")
        val existing = repository.findByUrl(normalizedUrl)
        println("DEBUG: URL check result: ${existing != null}")
        if (existing != null) {
            println("DEBUG: URL already exists: ${request.url}")
            return existing.toRecord()
        }

        println("DEBUG: Detecting parser type for: $normalizedUrl")
        val parserType = request.parserType ?: detectParserType(normalizedUrl)
        println("DEBUG: Detected parser type: $parserType for ${request.url}")
        val inserted = repository.insert(normalizedUrl, parserType)
        println("DEBUG: Inserted URL: ${request.url}")
        return inserted.toRecord()
    }

    override suspend fun resetAll() {
        repository.resetAllToPending()
    }

    override suspend fun remove(id: String): Boolean {
        val identifier = id.toIntOrNull() ?: return false
        return repository.delete(identifier)
    }

    private suspend fun detectParserType(url: String): ParserType {
        println("DEBUG: Detecting parser for $url")
        val parser = runCatching { parserRegistry.forUrl(url) }.getOrNull()
        val resolved = parser?.supportedTypes?.firstOrNull()
        if (resolved != null) {
            println("DEBUG: Detected parser $resolved for $url")
            return resolved
        }
        println("DEBUG: No parser matched $url; falling back to GENERIC_HTML")
        return ParserType.GENERIC_HTML
    }

    private fun dev.staticvar.mcp.shared.model.SourceUrl.toRecord(): SourceUrlRecord =
        SourceUrlRecord(
            id = id,
            url = url,
            parserType = parserType,
            status = status,
            lastCrawled = lastCrawled,
            etag = etag,
            lastModified = lastModified,
            errorMessage = errorMessage,
        )

    private companion object {
        private val logger = KotlinLogging.logger {}
    }
}
