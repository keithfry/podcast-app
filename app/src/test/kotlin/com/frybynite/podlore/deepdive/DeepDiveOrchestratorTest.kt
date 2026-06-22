package com.frybynite.podlore.deepdive

import com.frybynite.podlore.data.db.dao.DeepDiveDao
import com.frybynite.podlore.data.db.dao.EpisodeDao
import com.frybynite.podlore.data.db.dao.PodcastDao
import com.frybynite.podlore.data.db.entities.DeepDiveEntity
import com.frybynite.podlore.data.storage.CacheStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals

class DeepDiveOrchestratorTest {
    @get:Rule val tmp = TemporaryFolder()

    private val fetcher = mockk<UrlContentFetcher>()
    private val summarizer = mockk<TextSummarizer>()
    private val tts = mockk<TtsSynthesizer>()
    private val client = mockk<OkHttpClient>(relaxed = true)
    private val deepDiveDao = mockk<DeepDiveDao>(relaxed = true)
    private val episodeDao = mockk<EpisodeDao>(relaxed = true)
    private val podcastDao = mockk<PodcastDao>(relaxed = true)
    private fun storage() = CacheStorage(tmp.newFolder("podcasts${System.nanoTime()}"))

    private fun orchestrator(storage: CacheStorage) =
        DeepDiveOrchestrator(fetcher, summarizer, tts, client, storage, deepDiveDao, episodeDao, podcastDao)

    @Test fun `process with no episode generates without caching`() = runTest {
        every { fetcher.fetch("https://example.com") } returns "Article text content"
        coEvery { summarizer.summarize("Article text content", any()) } returns "Short summary."
        val fakeFile = File(tmp.newFolder(), "tts.wav").apply { writeText("x") }
        coEvery { tts.synthesizeToFile("Short summary.") } returns fakeFile

        val result = orchestrator(storage()).process("https://example.com", null)

        assertEquals(fakeFile, result)
        coVerify(exactly = 1) { summarizer.summarize("Article text content", any()) }
    }

    @Test fun `process returns cached file without fetching or synthesizing on hit`() = runTest {
        val cached = File(tmp.newFolder(), "more.wav").apply { writeText("audio") }
        coEvery { deepDiveDao.get("https://cdn/ep1.mp3", "https://news/x") } returns
            DeepDiveEntity("https://cdn/ep1.mp3", "https://news/x", cached.absolutePath, "sum", 1L)

        val result = orchestrator(storage()).process("https://news/x", "https://cdn/ep1.mp3")

        assertEquals(cached.absolutePath, result.absolutePath)
        coVerify(exactly = 0) { fetcher.fetch(any()) }
        coVerify(exactly = 0) { tts.synthesizeToFile(any()) }
    }
}
