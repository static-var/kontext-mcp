package dev.staticvar.mcp.parser.chunk

import dev.staticvar.mcp.shared.config.ChunkingConfig
import dev.staticvar.mcp.shared.model.DocumentChunk

/**
 * Domain-specific representation of an extracted section (e.g., an <h2> block plus paragraphs).
 */
data class ContentSection(
    val title: String?,
    val hierarchy: List<String>,
    val body: String,
    val metadata: Map<String, String> = emptyMap(),
    val codeLanguage: String? = null
)

/**
 * Combines extracted sections into embedding-friendly chunks while respecting token limits.
 */
class StructuredChunker(
    private val config: ChunkingConfig,
    private val tokenEstimator: TokenEstimator = TokenEstimator()
) {

    fun chunk(sections: List<ContentSection>): List<DocumentChunk> {
        if (sections.isEmpty()) return emptyList()

        val chunks = mutableListOf<DocumentChunk>()
        val buffer = StringBuilder()
        val mergedMetadata = linkedMapOf<String, String>()
        var currentHierarchy: List<String> = emptyList()
        var currentTokens = 0
        var overlapSeed = ""

        fun flush(force: Boolean = false) {
            if (buffer.isEmpty()) return
            val content = buffer.toString().trim()
            if (content.isEmpty()) {
                buffer.clear()
                mergedMetadata.clear()
                currentTokens = 0
                return
            }

            chunks += DocumentChunk(
                content = content,
                sectionHierarchy = currentHierarchy,
                codeLanguage = null,
                metadata = mergedMetadata.toMap()
            )

            overlapSeed = buildOverlapSeed(content)
            buffer.clear()
            mergedMetadata.clear()
            currentTokens = 0

            if (config.overlapTokens > 0 && overlapSeed.isNotEmpty() && !force) {
                buffer.append(overlapSeed).append("\n\n")
                currentTokens = tokenEstimator.countTokens(overlapSeed)
            }
        }

        sections.forEach { section ->
            val normalized = section.body.trim()
            if (normalized.isEmpty()) return@forEach

            val isCodeSection = section.codeLanguage != null || section.metadata["section_type"] == "code"
            if (isCodeSection) {
                flush(force = true)
                chunks += DocumentChunk(
                    content = normalized,
                    sectionHierarchy = section.hierarchy,
                    codeLanguage = section.codeLanguage,
                    metadata = if (section.metadata.isEmpty()) {
                        mapOf("section_type" to "code")
                    } else {
                        section.metadata
                    }
                )
                overlapSeed = ""
                return@forEach
            }

            val tokens = tokenEstimator.countTokens(normalized)
            if (buffer.isEmpty()) {
                currentHierarchy = section.hierarchy
            }

            val appendable = ensureMaxTokenSegment(normalized)
            appendable.forEachIndexed { index, segment ->
                val segmentTokens = tokenEstimator.countTokens(segment)
                if (buffer.isNotEmpty() &&
                    currentTokens + segmentTokens > config.targetTokens &&
                    currentTokens > 0
                ) {
                    flush()
                }

                if (buffer.isEmpty() && overlapSeed.isNotEmpty()) {
                    currentHierarchy = section.hierarchy
                }

                if (buffer.isEmpty()) {
                    currentHierarchy = section.hierarchy
                }

                buffer.append(segment)
                if (!segment.endsWith("\n")) buffer.append("\n\n")
                currentTokens += segmentTokens
            }

            mergedMetadata.putAll(section.metadata)
        }

        flush(force = true)
        return chunks
    }

    private fun ensureMaxTokenSegment(text: String): List<String> {
        if (tokenEstimator.countTokens(text) <= config.maxTokens) {
            return listOf(text)
        }
        val sentences = sentenceSplit(text)
        val segments = mutableListOf<String>()
        val builder = StringBuilder()
        var tokens = 0
        sentences.forEach { sentence ->
            val estimated = tokenEstimator.countTokens(sentence)
            if (builder.isNotEmpty() && tokens + estimated > config.maxTokens) {
                segments += builder.toString().trim()
                builder.clear()
                tokens = 0
            }
            builder.append(sentence).append(" ")
            tokens += estimated
        }
        if (builder.isNotEmpty()) {
            segments += builder.toString().trim()
        }
        return segments
    }

    private fun buildOverlapSeed(content: String): String {
        if (config.overlapTokens <= 0) return ""
        val sentences = sentenceSplit(content).asReversed()
        val selected = mutableListOf<String>()
        var tokens = 0
        for (sentence in sentences) {
            val estimated = tokenEstimator.countTokens(sentence)
            if (tokens > 0 && tokens + estimated > config.overlapTokens) break
            selected.add(0, sentence)
            tokens += estimated
            if (tokens >= config.overlapTokens) break
        }
        return selected.joinToString(" ").trim()
    }

    private fun sentenceSplit(text: String): List<String> =
        SENTENCE_SPLIT_REGEX.split(text)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private companion object {
        private val SENTENCE_SPLIT_REGEX =
            Regex("(?<=[.!?])\\s+(?=[A-Z0-9\"'])")
    }
}
