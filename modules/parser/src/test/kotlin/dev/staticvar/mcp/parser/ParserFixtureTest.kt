package dev.staticvar.mcp.parser

import dev.staticvar.mcp.parser.api.ParseRequest
import dev.staticvar.mcp.parser.api.ParserContext
import dev.staticvar.mcp.parser.registry.ParserRegistry
import dev.staticvar.mcp.shared.model.ContentType
import dev.staticvar.mcp.shared.model.ParserType
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class ParserFixtureTest {

    private lateinit var registry: ParserRegistry

    @BeforeTest
    fun setUp() {
        registry = ParserModule.defaultRegistry()
    }

    @Test
    fun `android baseline profiles overview fixture`() = kotlinx.coroutines.runBlocking {
        val request = requestFromFixture(
            "https://developer.android.com/topic/performance/baselineprofiles/overview",
            "fixtures/android/baselineprofiles-overview.html"
        )
        val parser = registry.forType(ParserType.ANDROID_DOCS)!!
        val result = parser.parse(request, ParserContext())

        assertEquals(ContentType.GUIDE, result.document.contentType)
        assertTrue(result.chunks.isNotEmpty())
        val actualPaths = result.discoveredLinks.mapNotNull { runCatching { URI(it).path }.getOrNull() }
        assertTrue(actualPaths.any { it.contains("baselineprofiles") })
    }

    @Test
    fun `android room release notes fixture`() = kotlinx.coroutines.runBlocking {
        val request = requestFromFixture(
            "https://developer.android.com/jetpack/androidx/releases/room",
            "fixtures/android/room-release-notes.html"
        )
        val parser = registry.forType(ParserType.ANDROID_DOCS)!!
        val result = parser.parse(request, ParserContext())

        assertEquals(ContentType.RELEASE_NOTES, result.document.contentType)
        assertTrue(result.chunks.isNotEmpty())
        assertEquals("androidx.room", result.document.metadata["library"])
    }

    @Test
    fun `android room reference fixture`() = kotlinx.coroutines.runBlocking {
        val request = requestFromFixture(
            "https://developer.android.com/reference/androidx/room/Room",
            "fixtures/android/room-reference.html"
        )
        val parser = registry.forType(ParserType.ANDROID_DOCS)!!
        val result = parser.parse(request, ParserContext())

        assertEquals(ContentType.API_REFERENCE, result.document.contentType)
        assertEquals("Room", result.document.metadata["symbol"])
        assertTrue(result.chunks.isNotEmpty())
    }

    @Test
    fun `jetbrains exposed home fixture`() = kotlinx.coroutines.runBlocking {
        val request = requestFromFixture(
            "https://www.jetbrains.com/help/exposed/home.html",
            "fixtures/jetbrains/exposed-home.html"
        )
        val parser = registry.forType(ParserType.MKDOCS)!!
        val context = ParserContext(resourceLoader = { _ ->
            Files.readAllBytes(Path.of("src/test/resources/fixtures/jetbrains/exposed-home.json"))
        })
        val result = parser.parse(request, context)

        assertEquals(ContentType.GUIDE, result.document.contentType)
        assertTrue(result.chunks.isNotEmpty() || result.discoveredLinks.isNotEmpty())
    }

    @Test
    fun `kotlin releases fixture`() = kotlinx.coroutines.runBlocking {
        val request = requestFromFixture(
            "https://kotlinlang.org/docs/releases.html",
            "fixtures/kotlin/releases.html"
        )
        val parser = registry.forType(ParserType.KOTLIN_LANG)!!
        val result = parser.parse(request, ParserContext())

        assertEquals(ContentType.RELEASE_NOTES, result.document.contentType)
        assertTrue(result.chunks.isNotEmpty())
    }

    @Test
    fun `kotlin markdown fixture`() = kotlinx.coroutines.runBlocking {
        val request = ParseRequest(
            url = "https://kotlinlang.org/docs/releases.md",
            rawBytes = Path.of("src/test/resources/fixtures/kotlin/releases.md").readBytes(),
            mediaType = "text/markdown"
        )
        val parser = registry.forType(ParserType.KOTLIN_LANG)!!
        val result = parser.parse(request, ParserContext())

        assertTrue(result.document.contentType != ContentType.UNKNOWN)
        assertTrue(result.chunks.isNotEmpty())
    }

    private fun requestFromFixture(url: String, fixture: String): ParseRequest =
        ParseRequest(
            url = url,
            rawBytes = Path.of("src/test/resources").resolve(fixture).readBytes(),
            mediaType = "text/html"
        )

    private fun loadJsonArray(path: String): List<String> {
        val jsonString = Files.readString(Path.of("src/test/resources").resolve(path))
        val json = Json { ignoreUnknownKeys = true }
        return json.decodeFromString(ListSerializer(String.serializer()), jsonString)
    }
}
