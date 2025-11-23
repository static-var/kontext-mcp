package dev.staticvar.mcp.shared.model

import kotlinx.serialization.Serializable

/**
 * Defines the type of parser to use for a given source URL.
 * Each parser is specialized for a specific documentation structure.
 */
@Serializable
enum class ParserType {
    /** Parser for developer.android.com documentation */
    ANDROID_DOCS,

    /** Parser for kotlinlang.org documentation */
    KOTLIN_LANG,

    /** Generic parser for MkDocs-based sites */
    MKDOCS,

    /** Generic parser for GitHub markdown files */
    GITHUB,

    /** Fallback generic HTML parser */
    GENERIC_HTML,
}
