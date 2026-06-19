package com.frybynite.podcastapp.ui.player

import com.frybynite.podcastapp.data.db.dao.DeepDiveDao
import com.frybynite.podcastapp.data.db.dao.EpisodeDao
import com.frybynite.podcastapp.data.db.dao.PodcastDao
import com.frybynite.podcastapp.data.db.entities.EpisodeEntity
import com.frybynite.podcastapp.data.preferences.SpeedPreferences
import com.frybynite.podcastapp.data.repository.ChapterRepository
import com.frybynite.podcastapp.data.repository.PodcastRepository
import com.frybynite.podcastapp.data.repository.TranscriptRepository
import com.frybynite.podcastapp.deepdive.DeepDiveOrchestrator
import com.frybynite.podcastapp.deepdive.ModelDownloadManager
import com.frybynite.podcastapp.deepdive.ModelDownloadState
import com.frybynite.podcastapp.deepdive.TextSummarizer
import com.frybynite.podcastapp.playback.PlaybackController
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelEpisodeSwitchTest {

    private val scheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(scheduler)

    private lateinit var episodeDao: EpisodeDao
    private lateinit var podcastDao: PodcastDao
    private lateinit var deepDiveDao: DeepDiveDao
    private lateinit var podcastRepo: PodcastRepository
    private lateinit var modelDownloadManager: ModelDownloadManager
    private lateinit var summarizer: TextSummarizer

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        episodeDao = mockk(relaxed = true)
        podcastDao = mockk(relaxed = true)
        deepDiveDao = mockk(relaxed = true)
        podcastRepo = mockk(relaxed = true)
        modelDownloadManager = mockk(relaxed = true) {
            every { state } returns MutableStateFlow(ModelDownloadState.Idle)
        }
        summarizer = mockk(relaxed = true) {
            every { isModelAvailable() } returns false
        }
        every { deepDiveDao.flowForEpisode(any()) } returns emptyFlow()
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun makeVm(summarizer: TextSummarizer = this.summarizer): PlayerViewModel =
        PlayerViewModel(
            context = mockk(relaxed = true),
            chapterRepo = mockk(relaxed = true),
            episodeDao = episodeDao,
            podcastDao = podcastDao,
            podcastRepo = podcastRepo,
            deepDiveDao = deepDiveDao,
            speedPrefs = mockk(relaxed = true) { every { speed } returns 1f },
            deepDiveOrchestrator = mockk(relaxed = true),
            summarizer = summarizer,
            modelDownloadManager = modelDownloadManager,
            transcriptRepo = mockk(relaxed = true),
            playbackController = mockk(relaxed = true) {
                every { controller } returns MutableStateFlow(null)
                every { currentlyPlayingUrl } returns MutableStateFlow(null)
                every { isPlaying } returns MutableStateFlow(false)
            }
        )

    private fun entity(
        audioUrl: String,
        title: String = "Title",
        durationSeconds: Int = 300,
        lastPositionMs: Long = 0L,
        isLiked: Boolean = false
    ) = EpisodeEntity(
        audioUrl = audioUrl,
        podcastFeedUrl = "https://feed.com",
        title = title,
        pubDate = 0L,
        durationSeconds = durationSeconds,
        chaptersUrl = null,
        downloadPath = "/data/ep.mp3",
        downloadStatus = "DONE",
        lastPositionMs = lastPositionMs,
        isLiked = isLiked
    )

    // --- Episode switching tests ---

    @Test fun `switching episodes resets position synchronously then loads new episode value`() = runTest {
        val vm = makeVm()
        coEvery { episodeDao.getByAudioUrl("https://ep1.mp3") } returns
            entity("https://ep1.mp3", lastPositionMs = 60_000L)
        coEvery { episodeDao.getByAudioUrl("https://ep2.mp3") } returns
            entity("https://ep2.mp3", lastPositionMs = 120_000L)
        coEvery { podcastDao.getByUrl(any()) } returns null

        vm.loadMetadata("https://ep1.mp3")
        advanceTimeBy(100)
        assertEquals(60_000L, vm.currentPositionMs.value)

        vm.loadMetadata("https://ep2.mp3")
        // Synchronous reset — position should be 0 before coroutine runs
        assertEquals(0L, vm.currentPositionMs.value)

        advanceTimeBy(100)
        assertEquals(120_000L, vm.currentPositionMs.value)
    }

    @Test fun `switching episodes resets isLiked synchronously then loads new episode value`() = runTest {
        val vm = makeVm()
        coEvery { episodeDao.getByAudioUrl("https://ep1.mp3") } returns
            entity("https://ep1.mp3", isLiked = true)
        coEvery { episodeDao.getByAudioUrl("https://ep2.mp3") } returns
            entity("https://ep2.mp3", isLiked = false)
        coEvery { podcastDao.getByUrl(any()) } returns null

        vm.loadMetadata("https://ep1.mp3")
        advanceTimeBy(100)
        assertTrue(vm.isLiked.value)

        vm.loadMetadata("https://ep2.mp3")
        // Synchronous reset
        assertFalse(vm.isLiked.value)

        advanceTimeBy(100)
        assertFalse(vm.isLiked.value)
    }

    @Test fun `switching episodes resets duration synchronously`() = runTest {
        val vm = makeVm()
        coEvery { episodeDao.getByAudioUrl("https://ep1.mp3") } returns
            entity("https://ep1.mp3", durationSeconds = 600)
        coEvery { episodeDao.getByAudioUrl("https://ep2.mp3") } returns
            entity("https://ep2.mp3", durationSeconds = 900)
        coEvery { podcastDao.getByUrl(any()) } returns null

        vm.loadMetadata("https://ep1.mp3")
        advanceTimeBy(100)
        assertEquals(600_000L, vm.durationMs.value)

        vm.loadMetadata("https://ep2.mp3")
        // Synchronous reset
        assertEquals(0L, vm.durationMs.value)

        advanceTimeBy(100)
        assertEquals(900_000L, vm.durationMs.value)
    }

    @Test fun `switching episodes updates episode title`() = runTest {
        val vm = makeVm()
        coEvery { episodeDao.getByAudioUrl("https://ep1.mp3") } returns
            entity("https://ep1.mp3", title = "Episode One")
        coEvery { episodeDao.getByAudioUrl("https://ep2.mp3") } returns
            entity("https://ep2.mp3", title = "Episode Two")
        coEvery { podcastDao.getByUrl(any()) } returns null

        vm.loadMetadata("https://ep1.mp3")
        advanceTimeBy(100)
        assertEquals("Episode One", vm.episodeTitle.value)

        vm.loadMetadata("https://ep2.mp3")
        advanceTimeBy(100)
        assertEquals("Episode Two", vm.episodeTitle.value)
    }

    @Test fun `switching to same episode reloads metadata`() = runTest {
        val vm = makeVm()
        coEvery { episodeDao.getByAudioUrl("https://ep1.mp3") } returns
            entity("https://ep1.mp3", title = "Episode One")
        coEvery { podcastDao.getByUrl(any()) } returns null

        vm.loadMetadata("https://ep1.mp3")
        advanceTimeBy(100)
        assertEquals("Episode One", vm.episodeTitle.value)

        vm.loadMetadata("https://ep1.mp3")
        advanceTimeBy(100)
        assertEquals("Episode One", vm.episodeTitle.value)
    }

    // --- moreAboutThis tests ---

    @Test fun `moreAboutThis with model unavailable sets ModelRequired state`() = runTest {
        val noModelSummarizer = mockk<TextSummarizer>(relaxed = true) {
            every { isModelAvailable() } returns false
        }
        val vm = makeVm(summarizer = noModelSummarizer)
        coEvery { episodeDao.getByAudioUrl("https://ep.mp3") } returns entity("https://ep.mp3")
        coEvery { podcastDao.getByUrl(any()) } returns null

        vm.loadMetadata("https://ep.mp3")
        advanceTimeBy(100)

        vm.moreAboutThis("https://ch.url", 0)

        assertEquals(DeepDiveState.ModelRequired, vm.deepDiveState.value)
    }

    @Test fun `moreAboutThis with ctrl null falls back to currentEpisode and sets deepDiveChapterIndex`() = runTest {
        val availableSummarizer = mockk<TextSummarizer>(relaxed = true) {
            every { isModelAvailable() } returns true
        }
        val vm = makeVm(summarizer = availableSummarizer)
        coEvery { episodeDao.getByAudioUrl("https://ep.mp3") } returns entity("https://ep.mp3")
        coEvery { podcastDao.getByUrl(any()) } returns null

        vm.loadMetadata("https://ep.mp3")
        advanceTimeBy(100)

        // ctrl is null (default in tests), deepDiveResumeEpisodeUri will be set from currentEpisode
        vm.moreAboutThis("https://ch.url", sourceChapterIndex = 3)

        // deepDiveChapterIndex is set synchronously before the coroutine launches
        assertEquals(3, vm.deepDiveChapterIndex.value)
    }

    @Test fun `moreAboutThis before loadMetadata is a no-op`() = runTest {
        val availableSummarizer = mockk<TextSummarizer>(relaxed = true) {
            every { isModelAvailable() } returns true
        }
        val vm = makeVm(summarizer = availableSummarizer)

        // Do NOT call loadMetadata — no currentEpisode set
        vm.moreAboutThis("https://ch.url", 0)

        assertNull(vm.deepDiveChapterIndex.value)
        assertEquals(DeepDiveState.Idle, vm.deepDiveState.value)
    }
}
