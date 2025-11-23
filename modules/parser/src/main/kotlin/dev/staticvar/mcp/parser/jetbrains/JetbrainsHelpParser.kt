package dev.staticvar.mcp.parser.jetbrains

import dev.staticvar.mcp.parser.api.ParseRequest
import dev.staticvar.mcp.parser.api.ParserContext
import dev.staticvar.mcp.parser.html.BaseHtmlParser
import dev.staticvar.mcp.parser.html.HtmlSectionExtractor
import dev.staticvar.mcp.shared.model.ContentType
import dev.staticvar.mcp.shared.model.ParserType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jsoup.Jsoup
import java.net.URI
import org.jsoup.nodes.Document as JsoupDocument

class JetbrainsHelpParser(
    extractor: HtmlSectionExtractor = HtmlSectionExtractor(),
) : BaseHtmlParser(extractor) {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var lastLinks: List<String> = emptyList()

    override val supportedTypes: Set<ParserType> = setOf(ParserType.MKDOCS)

    override fun matches(uri: URI): Boolean = uri.host?.endsWith("jetbrains.com") == true && uri.path.contains("/help/")

    override fun determineContentType(
        uri: URI,
        document: JsoupDocument,
    ): ContentType = ContentType.GUIDE

    override fun buildMetadata(
        document: JsoupDocument,
        uri: URI,
    ): Map<String, String> {
        val metadata = mutableMapOf<String, String>()
        document.select("meta[name=description]").firstOrNull()?.attr("content")?.let {
            metadata["description"] = it
        }
        document.select("meta[property='og:title']").firstOrNull()?.attr("content")?.let {
            metadata["og_title"] = it
        }
        metadata["source"] = "jetbrains_help"
        metadata["url"] = uri.toString()
        return metadata
    }

    override fun discoverLinks(
        document: JsoupDocument,
        uri: URI,
    ): List<String> = if (lastLinks.isNotEmpty()) lastLinks else emptyList()

    override suspend fun preprocessDocument(
        request: ParseRequest,
        document: JsoupDocument,
        context: ParserContext,
    ): JsoupDocument {
        lastLinks = emptyList()
        val body = document.body() ?: return document
        val topic = body.attr("data-topic").takeIf { it.isNotBlank() } ?: return document
        val loader = context.resourceLoader ?: return document
        val baseUri = URI(request.url)
        val topicUrl = baseUri.resolve(topic).toString()
        val bytes = runCatching { loader(topicUrl) }.getOrNull() ?: return document

        val jsonRoot =
            runCatching { json.parseToJsonElement(bytes.decodeToString()).jsonObject }
                .getOrNull() ?: return document

        val rendered = renderWritersideJson(jsonRoot, baseUri)
        lastLinks = rendered.links
        return Jsoup.parse(rendered.html, request.url)
    }

    private fun renderWritersideJson(
        root: JsonObject,
        baseUri: URI,
    ): RenderedContent {
        val builder = StringBuilder()
        val links = mutableSetOf<String>()
        root["title"]?.asText()?.let {
            builder.append("<h1>").append(it).append("</h1>")
        }
        root["subtitle"]?.asText()?.let {
            builder.append("<p>").append(it).append("</p>")
        }
        root["tips"]?.let { renderCardSection(builder, "Tips", it, baseUri, links) }
        root["main"]?.let { renderGroup(builder, it, baseUri, links) }
        root["highlighted"]?.let { renderGroup(builder, it, baseUri, links) }
        return RenderedContent(builder.toString(), links.toList())
    }

    private fun renderGroup(
        builder: StringBuilder,
        element: JsonElement,
        baseUri: URI,
        links: MutableSet<String>,
    ) {
        val obj = element as? JsonObject ?: return
        obj["title"]?.asText()?.let { title ->
            builder.append("<h2>").append(title).append("</h2>")
        }
        obj["data"]?.let { renderCardSection(builder, null, it, baseUri, links) }
    }

    private fun renderCardSection(
        builder: StringBuilder,
        heading: String?,
        element: JsonElement,
        baseUri: URI,
        links: MutableSet<String>,
    ) {
        val array = element as? JsonArray ?: return
        if (heading != null) {
            builder.append("<h2>").append(heading).append("</h2>")
        }
        array.forEach { cardElement ->
            val card = cardElement as? JsonObject ?: return@forEach
            val title = card["title"]?.asText()
            val description = card["description"]?.asText()
            val url = card["url"]?.asText()
            if (!title.isNullOrBlank()) {
                builder.append("<h3>").append(title).append("</h3>")
            }
            if (!description.isNullOrBlank()) {
                builder.append("<p>").append(description).append("</p>")
            }
            if (!url.isNullOrBlank()) {
                val absolute = runCatching { baseUri.resolve(url) }.getOrNull()?.toString() ?: url
                links += absolute
                builder.append("<p><a href=\"").append(absolute).append("\">").append(absolute).append("</a></p>")
            }
        }
    }

    private fun JsonElement.asText(): String? =
        (this as? kotlinx.serialization.json.JsonPrimitive)
            ?.content
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private data class RenderedContent(
        val html: String,
        val links: List<String>,
    )
}
