package dev.staticvar.mcp.parser.html

import dev.staticvar.mcp.parser.chunk.ContentSection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Converts HTML documents into a list of [ContentSection] objects,
 * preserving heading hierarchy and code blocks.
 */
class HtmlSectionExtractor {
    fun extract(
        rawHtml: String,
        baseUrl: String? = null,
    ): List<ContentSection> {
        val document = Jsoup.parse(rawHtml, baseUrl ?: "")
        return extract(document)
    }

    fun extract(document: Document): List<ContentSection> {
        val body = document.body() ?: return emptyList()
        val contentRoot =
            body.selectFirst("main, article, div.devsite-article-body, div.docs-article, div[class*=article-body]")
                ?: body
        val sections = mutableListOf<ContentSection>()
        val hierarchy = ArrayDeque<String>()
        val buffer = StringBuilder()

        fun flush() {
            val text = buffer.toString().trim()
            if (text.isEmpty()) {
                buffer.clear()
                return
            }

            sections +=
                ContentSection(
                    title = hierarchy.lastOrNull(),
                    hierarchy = hierarchy.toList(),
                    body = text,
                    metadata = emptyMap(),
                )

            buffer.clear()
        }

        contentRoot.select("h1, h2, h3, h4, h5, h6, p, pre, table, ul, ol, blockquote").forEach { element ->
            when {
                element.tagName().matches(HEADING_REGEX) -> {
                    flush()
                    val level = element.tagName().substring(1).toIntOrNull() ?: 1
                    while (hierarchy.size >= level) hierarchy.removeLast()
                    hierarchy.addLast(element.text().trim())
                }

                element.tagName() == "pre" -> {
                    flush()
                    val codeElement = element.selectFirst("code")
                    val codeText = codeElement?.wholeText() ?: element.text()
                    val language = extractLanguage(codeElement)
                    val formatted = formatCodeBlock(codeText.trim(), language)
                    sections +=
                        ContentSection(
                            title = hierarchy.lastOrNull(),
                            hierarchy = hierarchy.toList(),
                            body = formatted,
                            metadata = mapOf("section_type" to "code"),
                            codeLanguage = language,
                        )
                }

                element.tagName() == "table" -> {
                    appendBlock(buffer, renderTable(element))
                }

                element.tagName() == "ul" || element.tagName() == "ol" -> {
                    appendBlock(buffer, renderList(element, element.tagName() == "ol"))
                }

                element.hasText() -> {
                    val text = element.text().trim()
                    if (text.isNotEmpty()) {
                        appendBlock(buffer, text)
                    }
                }
            }
        }

        flush()
        return sections
    }

    private fun appendBlock(
        buffer: StringBuilder,
        text: String,
    ) {
        if (buffer.isNotEmpty()) buffer.append("\n\n")
        buffer.append(text)
    }

    private fun extractLanguage(codeElement: Element?): String? {
        if (codeElement == null) return null
        val classAttr = codeElement.classNames().firstOrNull { it.startsWith("language-") || it.startsWith("lang-") }
        return classAttr?.substringAfter('-', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
    }

    private fun renderList(
        element: Element,
        ordered: Boolean,
    ): String {
        val items = element.select("> li")
        if (items.isEmpty()) return element.text()
        val builder = StringBuilder()
        items.forEachIndexed { index, item ->
            val prefix = if (ordered) "${index + 1}." else "•"
            builder.append(prefix).append(' ').append(item.text().trim()).append('\n')
        }
        return builder.toString().trim()
    }

    private fun renderTable(element: Element): String {
        val rows = element.select("tr")
        if (rows.isEmpty()) return element.text()
        val table =
            rows.map { row ->
                row.select("th, td").map { cell -> cell.text().trim() }
            }

        val columnWidths = mutableListOf<Int>()
        table.forEach { row ->
            row.forEachIndexed { index, cell ->
                if (columnWidths.size <= index) {
                    columnWidths.add(cell.length)
                } else {
                    columnWidths[index] = maxOf(columnWidths[index], cell.length)
                }
            }
        }

        val builder = StringBuilder()
        table.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { columnIndex, cell ->
                val width = columnWidths.getOrElse(columnIndex) { cell.length }
                builder.append(cell.padEnd(width))
                if (columnIndex < row.lastIndex) builder.append(" | ")
            }
            builder.append('\n')
            val headerRow = rows.firstOrNull()
            if (rowIndex == 0 && headerRow != null && headerRow.select("th").isNotEmpty()) {
                columnWidths.forEachIndexed { index, width ->
                    builder.append("".padEnd(width, '-'))
                    if (index < columnWidths.lastIndex) builder.append("-+-")
                }
                builder.append('\n')
            }
        }

        return builder.toString().trim()
    }

    private fun formatCodeBlock(
        code: String,
        language: String?,
    ): String =
        buildString {
            append("```")
            if (!language.isNullOrBlank()) append(language)
            append('\n')
            append(code)
            if (!code.endsWith('\n')) append('\n')
            append("```")
        }

    private companion object {
        private val HEADING_REGEX = Regex("^h[1-6]$")
    }
}
