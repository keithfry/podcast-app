package com.podcastapp.data.repository

import com.podcastapp.data.db.dao.EpisodeDao
import com.podcastapp.data.db.dao.PodcastDao
import com.podcastapp.data.db.entities.EpisodeEntity
import com.podcastapp.data.db.entities.PodcastEntity
import com.podcastapp.data.network.FeedApi
import com.podcastapp.data.network.RssParser
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

class PodcastRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repo: PodcastRepository
    private val podcastDao = mockk<PodcastDao>(relaxed = true)
    private val episodeDao = mockk<EpisodeDao>(relaxed = true)

    @Before fun setUp() {
        server.start()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val feedApi = FeedApi(OkHttpClient(), moshi)
        repo = PodcastRepository(feedApi, RssParser(), podcastDao, episodeDao)
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun `addPodcast fetches feed and upserts podcast to db`() = runTest {
        server.enqueue(MockResponse().setBody(SAMPLE_XML).setResponseCode(200))
        val url = server.url("/feed.xml").toString()

        repo.addPodcast(url)

        coVerify { podcastDao.upsert(any()) }
    }

    @Test fun `addPodcast saves episodes to db`() = runTest {
        server.enqueue(MockResponse().setBody(SAMPLE_XML).setResponseCode(200))
        val url = server.url("/feed.xml").toString()

        repo.addPodcast(url)

        val slot = slot<List<EpisodeEntity>>()
        coVerify { episodeDao.upsertAll(capture(slot)) }
        assertEquals(1, slot.captured.size)
        assertEquals("Episode 1", slot.captured[0].title)
    }

    @Test fun `addPodcast sets feedUrl on podcast`() = runTest {
        server.enqueue(MockResponse().setBody(SAMPLE_XML).setResponseCode(200))
        val url = server.url("/feed.xml").toString()

        repo.addPodcast(url)

        val slot = slot<PodcastEntity>()
        coVerify { podcastDao.upsert(capture(slot)) }
        assertEquals(url, slot.captured.feedUrl)
    }

    companion object {
        val SAMPLE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"
              xmlns:podcast="https://podcastindex.org/namespace/1.0">
              <channel>
                <title>Test Podcast</title>
                <link>https://example.com</link>
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
