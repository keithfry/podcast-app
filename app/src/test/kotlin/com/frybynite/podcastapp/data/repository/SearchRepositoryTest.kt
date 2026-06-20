package com.frybynite.podcastapp.data.repository

import com.frybynite.podcastapp.data.db.dao.PodcastDao
import com.frybynite.podcastapp.data.network.SearchApi
import com.frybynite.podcastapp.domain.model.PodcastSearchResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchRepositoryTest {

    private val api = mockk<SearchApi>()
    private val dao = mockk<PodcastDao>()
    private val repo = SearchRepository(api, dao)

    private fun result(feedUrl: String, title: String = feedUrl) = PodcastSearchResult(
        feedUrl = feedUrl, title = title, author = "", artworkUrl = null, description = null
    )

    @Test fun `deduplicates by normalised URL, iTunes wins`() = runTest {
        coEvery { api.searchItunes(any()) } returns listOf(result("https://feed.example.com/", "iTunes"))
        coEvery { api.searchPodcastIndex(any()) } returns listOf(result("http://www.feed.example.com", "PodcastIndex"))
        coEvery { dao.existsByUrl(any()) } returns false

        val results = repo.search("test")

        assertEquals(1, results.size)
        assertEquals("iTunes", results[0].title)
    }

    @Test fun `merges non-duplicate results from both APIs`() = runTest {
        coEvery { api.searchItunes(any()) } returns listOf(result("https://a.example.com/feed"))
        coEvery { api.searchPodcastIndex(any()) } returns listOf(result("https://b.example.com/feed"))
        coEvery { dao.existsByUrl(any()) } returns false

        val results = repo.search("test")

        assertEquals(2, results.size)
    }

    @Test fun `returns empty list when both APIs fail`() = runTest {
        coEvery { api.searchItunes(any()) } throws Exception("network error")
        coEvery { api.searchPodcastIndex(any()) } throws Exception("network error")
        coEvery { dao.existsByUrl(any()) } returns false

        val results = repo.search("test")

        assertTrue(results.isEmpty())
    }

    @Test fun `isSubscribed populated from DAO`() = runTest {
        coEvery { api.searchItunes(any()) } returns listOf(result("https://sub.example.com/feed"))
        coEvery { api.searchPodcastIndex(any()) } returns emptyList()
        coEvery { dao.existsByUrl("https://sub.example.com/feed") } returns true

        val results = repo.search("test")

        assertTrue(results[0].isSubscribed)
    }

    @Test fun `isSubscribed false when not in DB`() = runTest {
        coEvery { api.searchItunes(any()) } returns listOf(result("https://new.example.com/feed"))
        coEvery { api.searchPodcastIndex(any()) } returns emptyList()
        coEvery { dao.existsByUrl(any()) } returns false

        val results = repo.search("test")

        assertFalse(results[0].isSubscribed)
    }

    @Test fun `normalizeUrl strips scheme, www, trailing slash`() {
        assertEquals("feed.example.com/rss", repo.normalizeUrl("https://www.feed.example.com/rss/"))
        assertEquals("feed.example.com/rss", repo.normalizeUrl("http://feed.example.com/rss"))
        assertEquals("feed.example.com/rss", repo.normalizeUrl("HTTPS://Feed.Example.Com/rss"))
    }
}
