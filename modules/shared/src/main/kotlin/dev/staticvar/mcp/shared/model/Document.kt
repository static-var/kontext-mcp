package dev.staticvar.mcp.shared.model

import kotlinx.datetime.serializers.InstantIso8601Serializer
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Represents a parsed documentation page.
 * Contains metadata for classification and retrieval optimization.
 */
@Serializable
data class Document(
    val id: Int,
    val sourceUrl: String,
    val title: String,
    val contentType: ContentType,
    val metadata: Map<String, String>,
    @Serializable(with = InstantIso8601Serializer::class)
    val lastIndexed: Instant,
)

/**
 * Represents a chunk of document content with its embedding vector.
 * Chunks preserve structural boundaries (headers, code blocks, etc.).
 */
@Serializable
data class DocumentChunk(
    val content: String,
    val sectionHierarchy: List<String> = emptyList(),
    val codeLanguage: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * Represents a stored chunk with its embedding in the database.
 */
data class EmbeddedChunk(
    val id: Int,
    val documentId: Int,
    val chunkIndex: Int,
    val content: String,
    val embedding: FloatArray,
    val metadata: Map<String, String>,
    val tokenCount: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EmbeddedChunk

        if (id != other.id) return false
        if (documentId != other.documentId) return false
        if (chunkIndex != other.chunkIndex) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + documentId
        result = 31 * result + chunkIndex
        return result
    }
}
