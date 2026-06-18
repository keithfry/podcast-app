package com.frybynite.podcastapp.data.repository

import androidx.work.WorkManager
import com.frybynite.podcastapp.data.db.dao.EpisodeDao
import com.frybynite.podcastapp.data.db.dao.PodcastDao
import com.frybynite.podcastapp.data.db.entities.PodcastEntity
import com.frybynite.podcastapp.data.network.FeedApi
import com.frybynite.podcastapp.data.network.RssParser
import com.frybynite.podcastapp.data.storage.CacheStorage
import com.frybynite.podcastapp.data.db.dao.DeepDiveDao
import com.frybynite.podcastapp.data.repository.TranscriptRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import app.cash.turbine.test
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

class PodcastRepositoryExtTest {

    private val server = MockWebServer()
    private lateinit var repo: PodcastRepository
    private val podcastDao = mockk<PodcastDao>(relaxed = true)
    private val episodeDao = mockk<EpisodeDao>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)
    private val cacheStorage = mockk<CacheStorage>(relaxed = true)
    private val transcriptRepository = mockk<TranscriptRepository>(relaxed = true)
    private val deepDiveDao = mockk<DeepDiveDao>(relaxed = true)

    @Before fun setUp() {
        server.start()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        repo = PodcastRepository(FeedApi(OkHttpClient(), moshi), RssParser(), podcastDao, episodeDao, workManager, cacheStorage, transcriptRepository, deepDiveDao)
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun `refreshPodcast re-fetches feed and updates episodes`() = runTest {
        server.enqueue(MockResponse().setBody(SAMPLE_XML).setResponseCode(200))
        val url = server.url("/feed.xml").toString()

        repo.refreshPodcast(url)

        assertEquals(1, server.requestCount)
        coVerify { podcastDao.upsert(any()) }
        coVerify { episodeDao.upsertFromFeed(any()) }
    }

    @Test fun `refreshPodcast updates existing podcast title`() = runTest {
        server.enqueue(MockResponse().setBody(SAMPLE_XML).setResponseCode(200))
        val url = server.url("/feed.xml").toString()

        repo.refreshPodcast(url)

        val slot = slot<PodcastEntity>()
        coVerify { podcastDao.upsert(capture(slot)) }
        assertEquals("Test Podcast", slot.captured.title)
        assertEquals(url, slot.captured.feedUrl)
    }

    @Test fun `removePodcast deletes existing podcast`() = runTest {
        val entity = PodcastEntity("https://feed.com", "Pod", "Author", "Desc", null, 0L)
        coEvery { podcastDao.getByUrl("https://feed.com") } returns entity

        repo.removePodcast("https://feed.com")

        coVerify { podcastDao.delete(entity) }
    }

    @Test fun `removePodcast is no-op when podcast not found`() = runTest {
        coEvery { podcastDao.getByUrl(any()) } returns null

        repo.removePodcast("https://not-there.com")

        coVerify(exactly = 0) { podcastDao.delete(any()) }
    }

    @Test fun `addPodcast throws on network error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val url = server.url("/feed.xml").toString()

        runCatching { repo.addPodcast(url) }.let { result ->
            assertEquals(true, result.isFailure)
        }
        coVerify(exactly = 0) { podcastDao.upsert(any()) }
    }

    @Test fun `episodesForPodcast maps entities to domain models`() = runTest {
        val entities = listOf(
            com.frybynite.podcastapp.data.db.entities.EpisodeEntity(
                audioUrl = "https://ep.mp3",
                podcastFeedUrl = "https://feed.com",
                title = "Ep 1",
                pubDate = 0L,
                durationSeconds = 120,
                chaptersUrl = null,
                downloadPath = null,
                downloadStatus = "NONE"
            )
        )
        coEvery { episodeDao.getForPodcast("https://feed.com") } returns
            kotlinx.coroutines.flow.flowOf(entities)

        repo.episodesForPodcast("https://feed.com").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("https://ep.mp3", list[0].audioUrl)
            assertEquals("Ep 1", list[0].title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    companion object {
        val SAMPLE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
              <channel>
                <title>Test Podcast</title>
                <description>A test podcast</description>
                <itunes:author>Test Author</itunes:author>
                <item>
                  <title>Episode 1</title>
                  <pubDate>Mon, 01 Jun 2026 08:00:00 +0000</pubDate>
                  <enclosure url="https://example.com/ep1.mp3" type="audio/mpeg" length="1000"/>
                  <itunes:duration>00:10:00</itunes:duration>
                  <guid isPermaLink="true">https://example.com/ep1.mp3</guid>
                </item>
              </channel>
            </rss>
        """.trimIndent()
    }
}
