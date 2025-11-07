package dev.staticvar.mcp.parser.generic

import dev.staticvar.mcp.parser.html.BaseHtmlParser
import dev.staticvar.mcp.parser.html.HtmlSectionExtractor
import dev.staticvar.mcp.shared.model.ContentType
import dev.staticvar.mcp.shared.model.ParserType
import java.net.URI
import org.jsoup.nodes.Document as JsoupDocument

class GenericHtmlParser(
    extractor: HtmlSectionExtractor = HtmlSectionExtractor()
) : BaseHtmlParser(extractor) {

    override val supportedTypes: Set<ParserType> = setOf(ParserType.GENERIC_HTML)

    override fun matches(uri: URI): Boolean = true

    override fun determineContentType(uri: URI, document: JsoupDocument): ContentType = ContentType.ARTICLE

    override fun buildMetadata(document: JsoupDocument, uri: URI): Map<String, String> =
        mapOf(
            "source" to "generic_html",
            "url" to uri.toString()
        )
}
