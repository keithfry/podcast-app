package com.frybynite.podcastapp.data.repository

import com.frybynite.podcastapp.data.db.dao.ChapterDao
import com.frybynite.podcastapp.data.network.FeedApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class ChapterRepositoryTest {

    private val server = MockWebServer()
    private lateinit var repo: ChapterRepository
    private val chapterDao = mockk<ChapterDao>(relaxed = true)

    @Before fun setUp() {
        server.start()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        repo = ChapterRepository(FeedApi(OkHttpClient(), moshi), chapterDao)
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun `fetchAndCacheChapters skips network on cache hit`() = runTest {
        coEvery { chapterDao.countForEpisode(any()) } returns 3

        repo.fetchAndCacheChapters("https://ep.mp3", server.url("/chapters.json").toString())

        assertEquals(0, server.requestCount)
        coVerify(exactly = 0) { chapterDao.replaceChaptersForEpisode(any(), any()) }
    }

    @Test fun `fetchAndCacheChapters fetches and saves chapters on cache miss`() = runTest {
        coEvery { chapterDao.countForEpisode(any()) } returns 0
        server.enqueue(MockResponse().setBody(CHAPTERS_JSON).setResponseCode(200))

        repo.fetchAndCacheChapters("https://ep.mp3", server.url("/chapters.json").toString())

        assertEquals(1, server.requestCount)
        val slot = slot<List<com.frybynite.podcastapp.data.db.entities.ChapterEntity>>()
        coVerify { chapterDao.replaceChaptersForEpisode(eq("https://ep.mp3"), capture(slot)) }
        assertEquals(2, slot.captured.size)
        assertEquals("Introduction", slot.captured[0].title)
        assertEquals("Main Topic", slot.captured[1].title)
    }

    @Test fun `fetchAndCacheChapters sets correct episodeAudioUrl on saved chapters`() = runTest {
        coEvery { chapterDao.countForEpisode(any()) } returns 0
        server.enqueue(MockResponse().setBody(CHAPTERS_JSON).setResponseCode(200))

        repo.fetchAndCacheChapters("https://ep.mp3", server.url("/chapters.json").toString())

        val slot = slot<List<com.frybynite.podcastapp.data.db.entities.ChapterEntity>>()
        coVerify { chapterDao.replaceChaptersForEpisode(any(), capture(slot)) }
        slot.captured.forEach { assertEquals("https://ep.mp3", it.episodeAudioUrl) }
    }

    @Test fun `fetchAndCacheChapters converts start and end times to milliseconds`() = runTest {
        coEvery { chapterDao.countForEpisode(any()) } returns 0
        server.enqueue(MockResponse().setBody(CHAPTERS_JSON).setResponseCode(200))

        repo.fetchAndCacheChapters("https://ep.mp3", server.url("/chapters.json").toString())

        val slot = slot<List<com.frybynite.podcastapp.data.db.entities.ChapterEntity>>()
        coVerify { chapterDao.replaceChaptersForEpisode(any(), capture(slot)) }
        assertEquals(0L, slot.captured[0].startTimeMs)
        assertEquals(30_000L, slot.captured[0].endTimeMs)
        assertEquals(30_000L, slot.captured[1].startTimeMs)
        assertEquals(300_000L, slot.captured[1].endTimeMs)
    }

    companion object {
        val CHAPTERS_JSON = """
            {
              "version": "1.2.0",
              "chapters": [
                {"startTime": 0, "endTime": 30, "title": "Introduction"},
                {"startTime": 30, "endTime": 300, "title": "Main Topic", "url": "https://example.com/ref"}
              ]
            }
        """.trimIndent()
    }
}
