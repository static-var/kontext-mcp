package dev.staticvar.mcp.server.evaluation

import dev.staticvar.mcp.shared.model.RetrievedChunk
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.InputStreamReader

object EvaluationDataset {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(resourcePath: String = "evaluation/queries.json"): List<QueryExpectation> {
        val resource =
            checkNotNull(javaClass.classLoader.getResource(resourcePath)) {
                "Evaluation dataset not found at $resourcePath"
            }

        resource.openStream().use { stream ->
            InputStreamReader(stream).use { reader ->
                val text = reader.readText()
                return json.decodeFromString(ListSerializer(QueryExpectation.serializer()), text)
            }
        }
    }
}

@Serializable
data class QueryExpectation(
    val query: String,
    @SerialName("expectedKeywords") val expectedKeywords: List<String>,
    @SerialName("minSimilarity") val minSimilarity: Float,
) {
    private val normalizedKeywords = expectedKeywords.map { it.lowercase() }

    fun relevanceFlags(chunks: List<RetrievedChunk>): List<Boolean> = chunks.map { matches(it) }

    fun matches(chunk: RetrievedChunk): Boolean {
        if (chunk.similarity < minSimilarity) return false
        val text = chunk.content.lowercase()
        val metadataValues = chunk.metadata.values.joinToString(separator = " ").lowercase()
        return normalizedKeywords.all { keyword ->
            text.contains(keyword) || metadataValues.contains(keyword)
        }
    }
}
