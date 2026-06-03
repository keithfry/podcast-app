package com.frybynite.podcastapp.deepdive

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UrlContentFetcherTest {
    private val server = MockWebServer()
    private lateinit var fetcher: UrlContentFetcher

    @Before fun setUp() {
        server.start()
        fetcher = UrlContentFetcher(OkHttpClient())
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun `extracts article text, excludes nav`() {
        server.enqueue(MockResponse().setBody("""
            <html><body>
              <article><p>Main content here.</p><p>Second paragraph.</p></article>
              <nav>Skip nav</nav>
            </body></html>
        """.trimIndent()).addHeader("Content-Type", "text/html"))
        val result = fetcher.fetch(server.url("/").toString())
        assertTrue(result.contains("Main content here."))
        assertFalse(result.contains("Skip nav"))
    }

    @Test fun `truncates to 3000 chars`() {
        val longText = "word ".repeat(1000)
        server.enqueue(MockResponse()
            .setBody("<html><body><article><p>$longText</p></article></body></html>")
            .addHeader("Content-Type", "text/html"))
        val result = fetcher.fetch(server.url("/").toString())
        assertTrue(result.length <= 3000)
    }

    @Test fun `falls back to body when no article tag`() {
        server.enqueue(MockResponse().setBody("""
            <html><body><p>Only body content.</p></body></html>
        """.trimIndent()).addHeader("Content-Type", "text/html"))
        val result = fetcher.fetch(server.url("/").toString())
        assertTrue(result.contains("Only body content."))
    }
}
