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
        val normalizedUrl = request.url.trim()
        require(normalizedUrl.isNotEmpty()) { "URL must not be blank" }

        val existing = repository.findByUrl(normalizedUrl)
        if (existing != null) {
            return existing.toRecord()
        }

        val parserType = request.parserType ?: detectParserType(normalizedUrl)
        val inserted = repository.insert(normalizedUrl, parserType)
        return inserted.toRecord()
    }

    override suspend fun remove(id: String): Boolean {
        val identifier = id.toIntOrNull() ?: return false
        return repository.delete(identifier)
    }

    private suspend fun detectParserType(url: String): ParserType {
        val parser = runCatching { parserRegistry.forUrl(url) }.getOrNull()
        val resolved = parser?.supportedTypes?.firstOrNull()
        if (resolved != null) {
            logger.debug { "Detected parser $resolved for $url" }
            return resolved
        }
        logger.warn { "No parser matched $url; falling back to GENERIC_HTML" }
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
