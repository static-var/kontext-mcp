package dev.staticvar.mcp.parser

import dev.staticvar.mcp.parser.api.ParseRequest
import dev.staticvar.mcp.parser.api.ParserContext
import dev.staticvar.mcp.parser.registry.ParserRegistry
import dev.staticvar.mcp.shared.model.ContentType
import dev.staticvar.mcp.shared.model.ParserType
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.contentType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ParserIntegrationTest {

    private lateinit var client: HttpClient
    private lateinit var registry: ParserRegistry

    @BeforeTest
    fun setUp() {
        client = HttpClient(CIO)
        registry = ParserModule.defaultRegistry()
    }

    @AfterTest
    fun tearDown() {
        client.close()
    }

    @Test
    fun `parse android baseline profiles overview`() = runBlocking {
        val url = "https://developer.android.com/topic/performance/baselineprofiles/overview"
        val parser = registry.forType(ParserType.ANDROID_DOCS)!!
        val request = fetch(url)
        val result = parser.parse(request, defaultContext())

        assertEquals(ContentType.GUIDE, result.document.contentType)
        assertTrue(result.chunks.isNotEmpty())
        assertTrue(result.discoveredLinks.any { it.contains("baselineprofiles") })
    }

    @Test
    fun `parse android room release notes`() = runBlocking {
        val url = "https://developer.android.com/jetpack/androidx/releases/room"
        val parser = registry.forType(ParserType.ANDROID_DOCS)!!
        val result = parser.parse(fetch(url), defaultContext())

        assertEquals(ContentType.RELEASE_NOTES, result.document.contentType)
        assertTrue(result.chunks.isNotEmpty())
    }

    @Test
    fun `parse android room api reference`() = runBlocking {
        val url = "https://developer.android.com/reference/androidx/room/Room"
        val parser = registry.forType(ParserType.ANDROID_DOCS)!!
        val result = parser.parse(fetch(url), defaultContext())

        assertEquals(ContentType.API_REFERENCE, result.document.contentType)
        assertTrue(result.chunks.isNotEmpty())
    }

    @Test
    fun `parse jetbrains exposed docs`() = runBlocking {
        val url = "https://www.jetbrains.com/help/exposed/home.html"
        val parser = registry.forType(ParserType.MKDOCS)!!
        val result = parser.parse(fetch(url), defaultContext())

        assertEquals(ContentType.GUIDE, result.document.contentType)
        assertTrue(result.chunks.isNotEmpty())
        assertTrue(result.discoveredLinks.isNotEmpty())
    }

    @Test
    fun `parse kotlin releases changelog`() = runBlocking {
        val url = "https://kotlinlang.org/docs/releases.html"
        val parser = registry.forType(ParserType.KOTLIN_LANG)!!
        val result = parser.parse(fetch(url), defaultContext())

        assertEquals(ContentType.RELEASE_NOTES, result.document.contentType)
        assertTrue(result.chunks.isNotEmpty())
    }

    @Test
    fun `parse kotlin idioms guide`() = runBlocking {
        val url = "https://kotlinlang.org/docs/idioms.html"
        val parser = registry.forType(ParserType.KOTLIN_LANG)!!
        val result = parser.parse(fetch(url), defaultContext())

        assertEquals(ContentType.GUIDE, result.document.contentType)
        assertTrue(result.chunks.isNotEmpty())
    }

    private suspend fun fetch(url: String): ParseRequest {
        val response: HttpResponse = client.get(url)
        val bytes = response.bodyAsBytes()
        val mediaType = response.contentType()?.toString()
        return ParseRequest(
            url = url,
            rawBytes = bytes,
            mediaType = mediaType
        )
    }

    private fun defaultContext(): ParserContext =
        ParserContext(resourceLoader = { resourceUrl ->
            client.get(resourceUrl).bodyAsBytes()
        })
}
