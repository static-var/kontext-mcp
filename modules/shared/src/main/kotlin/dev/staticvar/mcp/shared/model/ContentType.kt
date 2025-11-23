package dev.staticvar.mcp.shared.model

import kotlinx.serialization.Serializable

/**
 * Classification of documentation content for improved retrieval.
 * Different content types may require different chunking strategies.
 */
@Serializable
enum class ContentType {
    /** API reference documentation */
    API_REFERENCE,

    /** Tutorial or guide content */
    GUIDE,

    /** Hands-on tutorial or codelab */
    TUTORIAL,

    /** Release notes or changelog */
    RELEASE_NOTES,

    /** Code sample or example */
    CODE_SAMPLE,

    /** Conceptual overview */
    OVERVIEW,

    /** Blog post or article */
    ARTICLE,

    /** Unknown or mixed content */
    UNKNOWN,
}
