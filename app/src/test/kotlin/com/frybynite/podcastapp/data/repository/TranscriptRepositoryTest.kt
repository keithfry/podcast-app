package com.frybynite.podcastapp.data.repository

import android.util.Log
import com.frybynite.podcastapp.data.network.FeedApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class TranscriptRepositoryTest {

    private val server = MockWebServer()
    private lateinit var repo: TranscriptRepository
    private lateinit var tempDir: File

    @Before fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        server.start()
        tempDir = createTempDir()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        repo = TranscriptRepository(FeedApi(OkHttpClient(), moshi), moshi, tempDir)
    }

    @After fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    companion object {
        val TRANSCRIPT_JSON = """
            {
              "version": "1.0.0",
              "segments": [
                {"startTime": 0.0, "endTime": 3.2, "text": "Welcome."},
                {"startTime": 3.2, "endTime": 7.8, "text": "Today we begin."}
              ]
            }
        """.trimIndent()
    }

    @Test fun `fetchTranscript returns parsed segments`() = runTest {
        server.enqueue(MockResponse().setBody(TRANSCRIPT_JSON).setResponseCode(200))

        val segments = repo.fetchTranscript(server.url("/t.json").toString())

        assertEquals(2, segments.size)
        assertEquals("Welcome.", segments[0].text)
        assertEquals(0.0f, segments[0].startTimeSec)
        assertEquals(3.2f, segments[0].endTimeSec)
    }

    @Test fun `fetchTranscript caches to disk on first fetch`() = runTest {
        server.enqueue(MockResponse().setBody(TRANSCRIPT_JSON).setResponseCode(200))
        val url = server.url("/t.json").toString()

        repo.fetchTranscript(url)

        val cacheFile = File(tempDir, "${Math.abs(url.hashCode())}.json")
        assertEquals(true, cacheFile.exists())
    }

    @Test fun `fetchTranscript uses disk cache on second call`() = runTest {
        server.enqueue(MockResponse().setBody(TRANSCRIPT_JSON).setResponseCode(200))
        val url = server.url("/t.json").toString()

        repo.fetchTranscript(url)
        repo.fetchTranscript(url)

        assertEquals(1, server.requestCount)
    }

    @Test fun `fetchTranscript cache hit returns correct segments`() = runTest {
        server.enqueue(MockResponse().setBody(TRANSCRIPT_JSON).setResponseCode(200))
        val url = server.url("/t.json").toString()

        repo.fetchTranscript(url)
        val segments = repo.fetchTranscript(url)

        assertEquals(2, segments.size)
        assertEquals("Today we begin.", segments[1].text)
    }
}
