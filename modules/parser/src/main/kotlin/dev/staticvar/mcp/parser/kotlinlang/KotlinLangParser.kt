package dev.staticvar.mcp.parser.kotlinlang

import dev.staticvar.mcp.parser.api.ParseRequest
import dev.staticvar.mcp.parser.api.ParserContext
import dev.staticvar.mcp.parser.html.BaseHtmlParser
import dev.staticvar.mcp.parser.html.HtmlSectionExtractor
import dev.staticvar.mcp.shared.model.ContentType
import dev.staticvar.mcp.shared.model.ParserType
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import org.jsoup.Jsoup
import java.net.URI
import org.jsoup.nodes.Document as JsoupDocument

class KotlinLangParser(
    extractor: HtmlSectionExtractor = HtmlSectionExtractor(),
) : BaseHtmlParser(extractor) {
    override val supportedTypes: Set<ParserType> = setOf(ParserType.KOTLIN_LANG)

    override fun matches(uri: URI): Boolean = uri.host?.endsWith("kotlinlang.org") == true

    override suspend fun preprocessDocument(
        request: ParseRequest,
        document: JsoupDocument,
        context: ParserContext,
    ): JsoupDocument {
        val isMarkdown =
            request.mediaType?.contains("markdown", ignoreCase = true) == true ||
                request.url.endsWith(".md", ignoreCase = true)
        if (!isMarkdown) return super.preprocessDocument(request, document, context)

        val source = request.rawText
        val flavour = CommonMarkFlavourDescriptor()
        val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(source)
        val html = HtmlGenerator(source, parsedTree, flavour).generateHtml()
        return Jsoup.parse(html, request.url)
    }

    override fun determineContentType(
        uri: URI,
        document: JsoupDocument,
    ): ContentType =
        when {
            uri.path.contains("/api/") -> ContentType.API_REFERENCE
            uri.path.contains("releases") -> ContentType.RELEASE_NOTES
            else -> ContentType.GUIDE
        }

    override fun buildMetadata(
        document: JsoupDocument,
        uri: URI,
    ): Map<String, String> {
        val metadata = mutableMapOf<String, String>()
        document.select("meta[name=description]").firstOrNull()?.attr("content")?.let {
            metadata["description"] = it
        }
        metadata["source"] = "kotlin_docs"
        metadata["url"] = uri.toString()
        val path = uri.path.trim('/')
        metadata["path"] = "/$path"
        val segments = path.split('/').filter { it.isNotBlank() }
        if (segments.isNotEmpty()) {
            metadata["section"] = segments.first()
        }
        if (uri.path.contains("/api/")) {
            val apiPath = uri.path.substringAfter("/api/").trim('/')
            metadata["api_path"] = apiPath
            val parts = apiPath.split('/').filter { it.isNotBlank() }
            if (parts.size >= 2) {
                metadata["platform"] = parts[1]
            }
            if (parts.contains("stdlib")) {
                metadata["library"] = "kotlin-stdlib"
            }
            if (parts.lastOrNull()?.startsWith("-") == true && parts.size > 2) {
                metadata["symbol"] = parts.last().removePrefix("-")
                metadata["package"] = parts.dropLast(1).drop(2).joinToString(".")
            }
        }
        return metadata
    }

    override fun discoverLinks(
        document: JsoupDocument,
        uri: URI,
    ): List<String> =
        document.select("nav a[href], aside a[href]")
            .mapNotNull { node ->
                val resolved =
                    runCatching { uri.resolve(node.attr("href")) }.getOrNull()
                        ?: return@mapNotNull null
                if (resolved.host != uri.host) return@mapNotNull null
                resolved.normalize().toString()
            }
            .distinct()
}
