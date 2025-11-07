package dev.staticvar.mcp.parser.html

import dev.staticvar.mcp.parser.api.DocumentParser
import dev.staticvar.mcp.parser.api.ParseRequest
import dev.staticvar.mcp.parser.api.ParseResult
import dev.staticvar.mcp.parser.api.ParserContext
import dev.staticvar.mcp.parser.chunk.StructuredChunker
import dev.staticvar.mcp.shared.model.ContentType
import dev.staticvar.mcp.shared.model.Document
import java.net.URI
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document as JsoupDocument
import dev.staticvar.mcp.shared.model.ParserType

private val logger = KotlinLogging.logger {}

/**
 * Base implementation for HTML parsers that share the same extraction & chunking logic.
 */
abstract class BaseHtmlParser(
    private val extractor: HtmlSectionExtractor = HtmlSectionExtractor()
) : DocumentParser {

    abstract override val supportedTypes: Set<ParserType>

    final override suspend fun handles(url: String): Boolean =
        runCatching { URI(url) }.map(::matches).getOrDefault(false)

    final override suspend fun parse(request: ParseRequest, context: ParserContext): ParseResult {
        val uri = URI(request.url)
        val htmlDocument = Jsoup.parse(request.rawText, request.url)
        val processedDocument = preprocessDocument(request, htmlDocument, context)
        val sections = extractor.extract(processedDocument)
        val chunker = StructuredChunker(context.chunking)
        val chunks = chunker.chunk(sections)

        val parsedDocument = Document(
            id = 0,
            sourceUrl = request.url,
            title = extractTitle(processedDocument, uri),
            contentType = determineContentType(uri, processedDocument),
            metadata = buildMetadata(processedDocument, uri),
            lastIndexed = request.fetchedAt
        )

        if (chunks.isEmpty()) {
            logger.warn { "Parser ${this::class.simpleName} produced no chunks for ${request.url}" }
        }

        return ParseResult(
            document = parsedDocument,
            chunks = chunks,
            discoveredLinks = discoverLinks(processedDocument, uri)
        )
    }

    protected abstract fun matches(uri: URI): Boolean

    protected open suspend fun preprocessDocument(
        request: ParseRequest,
        document: JsoupDocument,
        context: ParserContext
    ): JsoupDocument = document

    protected open fun extractTitle(document: JsoupDocument, uri: URI): String {
        return document.selectFirst("h1")?.text()?.ifBlank { document.title() }?.trim().orEmpty()
    }

    protected abstract fun determineContentType(uri: URI, document: JsoupDocument): ContentType

    protected open fun buildMetadata(document: JsoupDocument, uri: URI): Map<String, String> =
        mapOf("url" to uri.toString())

    protected open fun discoverLinks(document: JsoupDocument, uri: URI): List<String> = emptyList()
}
