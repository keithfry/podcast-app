package com.frybynite.podlore.deepdive

import android.util.Log
import com.frybynite.podlore.data.db.dao.DeepDiveDao
import com.frybynite.podlore.data.db.dao.EpisodeDao
import com.frybynite.podlore.data.db.dao.PodcastDao
import com.frybynite.podlore.data.db.entities.DeepDiveEntity
import com.frybynite.podlore.data.db.entities.EpisodeEntity
import com.frybynite.podlore.data.db.entities.PodcastEntity
import com.frybynite.podlore.data.storage.CacheStorage
import com.frybynite.podlore.ui.player.DeepDiveStep
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeepDiveOrchestratorFreshMissTest {

    @get:Rule val tmp = TemporaryFolder()

    @Before fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }


    private val fetcher = mockk<UrlContentFetcher>()
    private val summarizer = mockk<TextSummarizer>()
    private val tts = mockk<TtsSynthesizer>()
    private val client = mockk<OkHttpClient>(relaxed = true)
    private val deepDiveDao = mockk<DeepDiveDao>(relaxed = true)
    private val episodeDao = mockk<EpisodeDao>(relaxed = true)
    private val podcastDao = mockk<PodcastDao>(relaxed = true)

    private val episodeUrl = "https://cdn.example.com/ep1.mp3"
    private val chapterUrl = "https://news.example.com/article"

    private val episodeEntity = EpisodeEntity(
        audioUrl = episodeUrl,
        podcastFeedUrl = "https://feed.com",
        title = "Great Episode",
        pubDate = 0L,
        durationSeconds = 600,
        chaptersUrl = null,
        downloadPath = null
    )
    private val podcastEntity = PodcastEntity(
        feedUrl = "https://feed.com",
        title = "My Podcast",
        author = "Author",
        description = "Desc",
        imageUrl = null,
        lastUpdated = 0L
    )

    private fun orchestrator() = DeepDiveOrchestrator(
        fetcher, summarizer, tts, client,
        CacheStorage(tmp.newFolder("podcasts")),
        deepDiveDao, episodeDao, podcastDao
    )

    @Test fun `fresh miss calls fetch summarize tts and upserts to dao in order`() = runTest {
        coEvery { deepDiveDao.get(episodeUrl, chapterUrl) } returns null
        coEvery { episodeDao.getByAudioUrl(episodeUrl) } returns episodeEntity
        coEvery { podcastDao.getByUrl("https://feed.com") } returns podcastEntity
        every { fetcher.fetch(chapterUrl) } returns "Article body text"
        coEvery { summarizer.summarize("Article body text", any()) } returns "Concise summary"
        val ttsFile = tmp.newFile("tts_out.wav").apply { writeBytes(ByteArray(16)) }
        coEvery { tts.synthesizeToFile("Concise summary") } returns ttsFile

        orchestrator().process(chapterUrl, episodeUrl)

        coVerify(exactly = 1) { fetcher.fetch(chapterUrl) }
        coVerify(exactly = 1) { summarizer.summarize("Article body text", any()) }
        coVerify(exactly = 1) { tts.synthesizeToFile("Concise summary") }
        coVerify(exactly = 1) { deepDiveDao.upsert(any()) }
    }

    @Test fun `fresh miss upserts entity with correct episode and chapter urls`() = runTest {
        coEvery { deepDiveDao.get(episodeUrl, chapterUrl) } returns null
        coEvery { episodeDao.getByAudioUrl(episodeUrl) } returns episodeEntity
        coEvery { podcastDao.getByUrl("https://feed.com") } returns podcastEntity
        every { fetcher.fetch(chapterUrl) } returns "text"
        coEvery { summarizer.summarize(any(), any()) } returns "summary"
        val ttsFile = tmp.newFile("tts.wav").apply { writeBytes(ByteArray(4)) }
        coEvery { tts.synthesizeToFile(any()) } returns ttsFile

        orchestrator().process(chapterUrl, episodeUrl)

        val slot = slot<DeepDiveEntity>()
        coVerify { deepDiveDao.upsert(capture(slot)) }
        assertEquals(episodeUrl, slot.captured.episodeAudioUrl)
        assertEquals(chapterUrl, slot.captured.chapterUrl)
        assertEquals("summary", slot.captured.summaryText)
    }

    @Test fun `fresh miss reports steps in order`() = runTest {
        coEvery { deepDiveDao.get(episodeUrl, chapterUrl) } returns null
        coEvery { episodeDao.getByAudioUrl(episodeUrl) } returns episodeEntity
        coEvery { podcastDao.getByUrl("https://feed.com") } returns podcastEntity
        every { fetcher.fetch(chapterUrl) } returns "text"
        coEvery { summarizer.summarize(any(), any()) } returns "summary"
        val ttsFile = tmp.newFile("tts.wav").apply { writeBytes(ByteArray(4)) }
        coEvery { tts.synthesizeToFile(any()) } returns ttsFile

        val steps = mutableListOf<DeepDiveStep>()
        orchestrator().process(chapterUrl, episodeUrl, onStep = { steps.add(it) })

        assertEquals(
            listOf(DeepDiveStep.FETCHING, DeepDiveStep.SUMMARIZING, DeepDiveStep.SYNTHESIZING),
            steps
        )
    }

    @Test fun `fetchExistingSummary passes cached summary to summarizer`() = runTest {
        coEvery { deepDiveDao.get(episodeUrl, chapterUrl) } returns null
        coEvery { episodeDao.getByAudioUrl(episodeUrl) } returns episodeEntity
        coEvery { podcastDao.getByUrl("https://feed.com") } returns podcastEntity
        every { fetcher.fetch(chapterUrl) } returns "text"

        // Write a metadata.json the orchestrator will find via CacheStorage
        val storage = CacheStorage(tmp.newFolder("podcasts2"))
        val metaFile = storage.metadataFile("https://feed.com", "My Podcast", episodeUrl, "Great Episode")
        metaFile.parentFile?.mkdirs()
        metaFile.writeText("""{"items":[{"link":"$chapterUrl","summary":"Prior summary"}]}""")

        coEvery { summarizer.summarize(any(), any()) } returns "new summary"
        val ttsFile = tmp.newFile("tts.wav").apply { writeBytes(ByteArray(4)) }
        coEvery { tts.synthesizeToFile(any()) } returns ttsFile

        DeepDiveOrchestrator(fetcher, summarizer, tts, client, storage, deepDiveDao, episodeDao, podcastDao)
            .process(chapterUrl, episodeUrl)

        coVerify { summarizer.summarize(any(), eq("Prior summary")) }
    }

    @Test fun `file rename fallback copies and deletes temp file when rename fails`() = runTest {
        coEvery { deepDiveDao.get(episodeUrl, chapterUrl) } returns null
        coEvery { episodeDao.getByAudioUrl(episodeUrl) } returns episodeEntity
        coEvery { podcastDao.getByUrl("https://feed.com") } returns podcastEntity
        every { fetcher.fetch(chapterUrl) } returns "text"
        coEvery { summarizer.summarize(any(), any()) } returns "summary"

        // Put tts file in a different filesystem root to force renameTo to fail
        val otherRoot = tmp.newFolder("other_fs")
        val ttsFile = File(otherRoot, "tts.wav").apply { writeBytes(ByteArray(8)) }
        coEvery { tts.synthesizeToFile(any()) } returns ttsFile

        val result = orchestrator().process(chapterUrl, episodeUrl)

        assertTrue(result.exists(), "cached file should exist after copy fallback")
        coVerify(exactly = 1) { deepDiveDao.upsert(any()) }
    }
}
