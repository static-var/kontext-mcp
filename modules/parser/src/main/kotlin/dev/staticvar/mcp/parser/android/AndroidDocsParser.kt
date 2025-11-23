package dev.staticvar.mcp.parser.android

import dev.staticvar.mcp.parser.api.ParseRequest
import dev.staticvar.mcp.parser.api.ParserContext
import dev.staticvar.mcp.parser.html.BaseHtmlParser
import dev.staticvar.mcp.parser.html.HtmlSectionExtractor
import dev.staticvar.mcp.shared.model.ContentType
import dev.staticvar.mcp.shared.model.ParserType
import java.net.URI
import org.jsoup.nodes.Document as JsoupDocument

/**
 * Parser implementation for developer.android.com content.
 */
class AndroidDocsParser(
    extractor: HtmlSectionExtractor = HtmlSectionExtractor(),
) : BaseHtmlParser(extractor) {
    @Volatile
    private var fallbackLinks: List<String> = emptyList()

    @Volatile
    private var lastRawHtml: String? = null

    @Volatile
    private var lastUrl: String? = null

    override val supportedTypes: Set<ParserType> = setOf(ParserType.ANDROID_DOCS)

    override fun matches(uri: URI): Boolean = uri.host?.endsWith("developer.android.com") == true

    override suspend fun preprocessDocument(
        request: ParseRequest,
        document: JsoupDocument,
        context: ParserContext,
    ): JsoupDocument {
        lastRawHtml = request.rawText
        lastUrl = request.url
        val baseUri = URI(request.url)
        val contentRoot =
            document.selectFirst(
                "article.devsite-article-body, div.devsite-article-body, main.devsite-main-content, devsite-content",
            ) ?: return super.preprocessDocument(request, document, context)
        val shell = JsoupDocument.createShell(request.url)
        shell.body().html(contentRoot.html())
        return if (shell.body().text().isBlank()) {
            val navItems =
                document.select("div.devsite-book-nav a[href] span.devsite-nav-text, nav.devsite-book-nav a[href] span.devsite-nav-text")
                    .map { it.text().trim() }
                    .filter { it.isNotEmpty() }
            fallbackLinks =
                document.select("div.devsite-book-nav a[href], nav.devsite-book-nav a[href]")
                    .mapNotNull { anchor ->
                        val href = anchor.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        runCatching { baseUri.resolve(href).normalize().toString() }.getOrNull()
                    }
                    .distinct()
            if (navItems.isNotEmpty()) {
                val fallback = JsoupDocument.createShell(request.url)
                val html =
                    buildString {
                        append("<h1>Navigation</h1><p>This page references the following topics:</p><ul>")
                        navItems.forEach { item ->
                            append("<li>").append(item).append("</li>")
                        }
                        append("</ul>")
                    }
                fallback.body().html(html)
                fallback
            } else {
                fallbackLinks =
                    document.select("a[href]")
                        .mapNotNull { anchor ->
                            val href = anchor.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            val resolved =
                                runCatching { baseUri.resolve(href).normalize().toString() }
                                    .getOrNull()
                                    ?: return@mapNotNull null
                            if (resolved.contains("developer.android.com")) resolved else null
                        }
                        .distinct()
                super.preprocessDocument(request, document, context)
            }
        } else {
            fallbackLinks = emptyList()
            shell
        }
    }

    override fun determineContentType(
        uri: URI,
        document: JsoupDocument,
    ): ContentType =
        when {
            uri.path.contains("/reference/") -> ContentType.API_REFERENCE
            uri.path.contains("/releases") -> ContentType.RELEASE_NOTES
            else -> ContentType.GUIDE
        }

    override fun extractTitle(
        document: JsoupDocument,
        uri: URI,
    ): String {
        val raw = document.title()
        val cleaned = raw.removeSuffix(" | Android Developers").trim()
        if (cleaned.isNotEmpty()) return cleaned
        return super.extractTitle(document, uri)
    }

    override fun buildMetadata(
        document: JsoupDocument,
        uri: URI,
    ): Map<String, String> {
        val metadata = super.buildMetadata(document, uri).toMutableMap()
        val path = uri.path.trim('/')
        metadata["path"] = "/$path"

        val segments = path.split('/').filter { it.isNotBlank() }
        if (segments.isNotEmpty()) {
            metadata["section"] = segments.first()
        }

        if (uri.path.contains("/reference/")) {
            val referencePath = uri.path.substringAfter("/reference/").trim('/')
            val parts = referencePath.split('/').filter { it.isNotBlank() }
            if (parts.isNotEmpty()) {
                metadata["symbol"] = parts.last()
            }
            if (parts.size > 1) {
                metadata["package"] = parts.dropLast(1).joinToString(".")
            }
            if (parts.isNotEmpty()) {
                metadata["library"] = parts.first().replace('-', '.')
            }
        }

        if (uri.path.contains("/jetpack/androidx/releases/")) {
            val lib = uri.path.substringAfter("/jetpack/androidx/releases/").trim('/')
            if (lib.isNotEmpty()) {
                metadata["library"] = "androidx.${lib.replace('/', '.')}"
            }
        }

        document.select("[data-apilevel]").firstOrNull()?.attr("data-apilevel")?.let {
            metadata["api_level"] = it
        }

        val breadcrumb =
            document.select("nav.breadcrumbs a[href]")
                .joinToString(" > ") { it.text() }
                .ifBlank {
                    document.select("ol[aria-label=breadcrumb] a[href]").joinToString(" > ") { it.text() }
                }
        if (breadcrumb.isNotBlank()) metadata["breadcrumbs"] = breadcrumb

        metadata["source"] = "android_docs"
        return metadata
    }

    override fun discoverLinks(
        document: JsoupDocument,
        uri: URI,
    ): List<String> {
        if (fallbackLinks.isNotEmpty()) return fallbackLinks
        val rawHtml = lastRawHtml
        val url = lastUrl
        if (!rawHtml.isNullOrBlank() && !url.isNullOrBlank()) {
            return deriveLinksFromRaw(url, rawHtml)
        }
        val root = uri.path.substringBeforeLast('/', "").let { if (it.isBlank()) "/" else it }
        return document.select("nav a[href], aside a[href]")
            .mapNotNull { element ->
                val resolved =
                    runCatching { uri.resolve(element.attr("href")) }.getOrNull()
                        ?: return@mapNotNull null
                if (resolved.host != uri.host) return@mapNotNull null
                if (!resolved.path.startsWith(root)) return@mapNotNull null
                resolved.normalize().toString()
            }
            .distinct()
    }

    private fun deriveLinksFromRaw(
        url: String,
        raw: String,
    ): List<String> {
        val baseUri = URI(url)
        val doc = org.jsoup.Jsoup.parse(raw, url)
        return doc.select("a[href]")
            .mapNotNull { element ->
                val href = element.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                runCatching { baseUri.resolve(href).normalize().toString() }.getOrNull()
            }
            .filter { it.contains("developer.android.com") }
            .distinct()
    }
}
