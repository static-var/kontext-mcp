@file:OptIn(kotlin.time.ExperimentalTime::class)

package dev.staticvar.mcp.parser.api

import dev.staticvar.mcp.shared.config.ChunkingConfig
import dev.staticvar.mcp.shared.model.Document
import dev.staticvar.mcp.shared.model.DocumentChunk
import dev.staticvar.mcp.shared.model.ParserType
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Common contract for all documentation parsers.
 */
interface DocumentParser {
    /**
     * Parser types supported by this implementation.
     */
    val supportedTypes: Set<ParserType>

    /**
     * Lightweight URL check that lets a parser opt-out before the expensive parsing phase.
     * Implementations may inspect the URL structure or even perform cheap HEAD requests.
     */
    suspend fun handles(url: String): Boolean = true

    /**
     * Parses the given [ParseRequest] into a [ParseResult].
     */
    suspend fun parse(
        request: ParseRequest,
        context: ParserContext = ParserContext(),
    ): ParseResult
}

/**
 * Input to a [DocumentParser].
 *
 * @param url canonical URL that uniquely identifies the page being parsed.
 * @param rawBytes original payload as downloaded (HTML, Markdown, etc).
 * @param mediaType best-effort media type of the payload (e.g. "text/html").
 * @param fetchedAt timestamp when the payload was retrieved.
 */
data class ParseRequest(
    val url: String,
    val rawBytes: ByteArray,
    val mediaType: String? = null,
    val fetchedAt: Instant = Clock.System.now(),
) {
    val rawText: String by lazy(LazyThreadSafetyMode.NONE) {
        rawBytes.toString(Charsets.UTF_8)
    }
}

/**
 * Provides configuration and helpers that parsers may rely on.
 */
data class ParserContext(
    val chunking: ChunkingConfig = ChunkingConfig(),
    val metadata: Map<String, Any?> = emptyMap(),
    val resourceLoader: (suspend (String) -> ByteArray)? = null,
)

/**
 * Output produced by a [DocumentParser].
 *
 * @property document metadata entry persisted in the documents table.
 * @property chunks ordered list of chunks suitable for embedding/indexing.
 */
data class ParseResult(
    val document: Document,
    val chunks: List<DocumentChunk>,
    val discoveredLinks: List<String> = emptyList(),
)
