package dev.staticvar.mcp.parser.registry

import dev.staticvar.mcp.parser.api.DocumentParser
import dev.staticvar.mcp.shared.model.ParserType

/**
 * Registry that maps [ParserType] or URL patterns to concrete [DocumentParser] implementations.
 */
class ParserRegistry(
    parsers: Collection<DocumentParser>
) {

    private val byType: Map<ParserType, DocumentParser>
    private val parserList: List<DocumentParser> = parsers.toList()

    init {
        val collisions = mutableMapOf<ParserType, MutableList<DocumentParser>>()
        val map = mutableMapOf<ParserType, DocumentParser>()
        parsers.forEach { parser ->
            parser.supportedTypes.forEach { type ->
                if (map.put(type, parser) != null) {
                    collisions.getOrPut(type) { mutableListOf() }.add(parser)
                }
            }
        }
        check(collisions.isEmpty()) {
            buildString {
                appendLine("Duplicate parser registrations detected:")
                collisions.forEach { (type, conflictingParsers) ->
                    appendLine("$type -> ${conflictingParsers.joinToString { it.javaClass.name }}")
                }
            }
        }
        byType = map.toMap()
    }

    /**
     * Returns the parser registered for [type], or null if none is found.
     */
    fun forType(type: ParserType): DocumentParser? = byType[type]

    /**
     * Attempts to find a parser capable of handling [url].
     */
    suspend fun forUrl(url: String): DocumentParser? =
        parserList.firstOrNull { parser -> parser.handles(url) }

    /**
     * Convenience accessor listing all registered parsers.
     */
    fun allParsers(): List<DocumentParser> = parserList
}
