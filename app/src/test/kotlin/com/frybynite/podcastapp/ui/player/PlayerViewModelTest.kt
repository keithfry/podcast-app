package com.frybynite.podcastapp.ui.player

import com.frybynite.podcastapp.data.db.dao.DeepDiveDao
import com.frybynite.podcastapp.data.db.dao.EpisodeDao
import com.frybynite.podcastapp.data.db.dao.PodcastDao
import com.frybynite.podcastapp.data.db.entities.EpisodeEntity
import com.frybynite.podcastapp.data.db.entities.PodcastEntity
import com.frybynite.podcastapp.data.preferences.SpeedPreferences
import com.frybynite.podcastapp.data.repository.ChapterRepository
import com.frybynite.podcastapp.data.repository.PodcastRepository
import com.frybynite.podcastapp.deepdive.DeepDiveOrchestrator
import com.frybynite.podcastapp.deepdive.ModelDownloadManager
import com.frybynite.podcastapp.deepdive.ModelDownloadState
import com.frybynite.podcastapp.deepdive.TextSummarizer
import io.mockk.coEvery
import io.mockk.coVerify
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
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(scheduler)

    private lateinit var episodeDao: EpisodeDao
    private lateinit var podcastDao: PodcastDao
    private lateinit var deepDiveDao: DeepDiveDao
    private lateinit var podcastRepo: PodcastRepository
    private lateinit var modelDownloadManager: ModelDownloadManager
    private lateinit var vm: PlayerViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        episodeDao = mockk(relaxed = true)
        podcastDao = mockk(relaxed = true)
        deepDiveDao = mockk(relaxed = true)
        podcastRepo = mockk(relaxed = true)
        modelDownloadManager = mockk(relaxed = true) {
            every { state } returns MutableStateFlow(ModelDownloadState.Idle)
        }
        every { deepDiveDao.flowForEpisode(any()) } returns emptyFlow()
        vm = PlayerViewModel(
            context = mockk(relaxed = true),
            chapterRepo = mockk(relaxed = true),
            episodeDao = episodeDao,
            podcastDao = podcastDao,
            podcastRepo = podcastRepo,
            deepDiveDao = deepDiveDao,
            speedPrefs = mockk(relaxed = true) { every { speed } returns 1f },
            deepDiveOrchestrator = mockk(relaxed = true),
            summarizer = mockk(relaxed = true),
            modelDownloadManager = modelDownloadManager
        )
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    // --- loadMetadata ---

    @Test fun `loadMetadata sets episodeTitle podcastTitle and podcastImageUrl from DB`() = runTest {
        val episodeEntity = EpisodeEntity(
            audioUrl = "https://ep.mp3",
            podcastFeedUrl = "https://feed.com",
            title = "Great Episode",
            pubDate = 0L,
            durationSeconds = 300,
            chaptersUrl = null,
            downloadPath = "/data/ep.mp3",
            downloadStatus = "DONE"
        )
        val podcastEntity = PodcastEntity(
            feedUrl = "https://feed.com",
            title = "My Podcast",
            author = "Author",
            description = "Desc",
            imageUrl = "https://img.com/art.jpg",
            lastUpdated = 0L
        )
        coEvery { episodeDao.getByAudioUrl("https://ep.mp3") } returns episodeEntity
        coEvery { podcastDao.getByUrl("https://feed.com") } returns podcastEntity

        vm.loadMetadata("https://ep.mp3")
        advanceTimeBy(100)

        assertEquals("Great Episode", vm.episodeTitle.value)
        assertEquals("My Podcast", vm.podcastTitle.value)
        assertEquals("https://img.com/art.jpg", vm.podcastImageUrl.value)
    }

    @Test fun `loadMetadata seeds currentPositionMs and durationMs from stored data`() = runTest {
        val episodeEntity = EpisodeEntity(
            audioUrl = "https://ep.mp3",
            podcastFeedUrl = "https://feed.com",
            title = "Ep",
            pubDate = 0L,
            durationSeconds = 600,
            chaptersUrl = null,
            downloadPath = "/data/ep.mp3",
            downloadStatus = "DONE",
            lastPositionMs = 120_000L
        )
        coEvery { episodeDao.getByAudioUrl("https://ep.mp3") } returns episodeEntity
        coEvery { podcastDao.getByUrl(any()) } returns null

        vm.loadMetadata("https://ep.mp3")
        advanceTimeBy(100)

        assertEquals(120_000L, vm.currentPositionMs.value)
        assertEquals(600_000L, vm.durationMs.value)
    }

    @Test fun `loadMetadata with unknown audioUrl leaves titles null`() = runTest {
        coEvery { episodeDao.getByAudioUrl(any()) } returns null

        vm.loadMetadata("https://unknown.mp3")
        advanceTimeBy(100)

        assertNull(vm.episodeTitle.value)
        assertNull(vm.podcastTitle.value)
    }

    @Test fun `loadMetadata resets stale state before coroutine completes`() = runTest {
        // Simulate previously loaded episode
        coEvery { episodeDao.getByAudioUrl(any()) } returns null
        vm.loadMetadata("https://old.mp3")
        advanceTimeBy(100)

        // Trigger new load — state should reset synchronously
        vm.loadMetadata("https://new.mp3")

        assertEquals(0L, vm.currentPositionMs.value)
        assertEquals(0L, vm.durationMs.value)
    }

    // --- setSleepTimer ---

    @Test fun `setSleepTimer initialises countdown and reaches zero`() = runTest {
        vm.setSleepTimer(1)

        assertEquals(60, vm.sleepTimerSeconds.value)
        advanceTimeBy(30_001)
        assertEquals(30, vm.sleepTimerSeconds.value)
        advanceTimeBy(30_001)
        assertNull(vm.sleepTimerSeconds.value)
    }

    @Test fun `setSleepTimer(0) cancels active timer`() = runTest {
        vm.setSleepTimer(1)
        advanceTimeBy(5_000)
        vm.setSleepTimer(0)
        assertNull(vm.sleepTimerSeconds.value)
    }

    @Test fun `setSleepTimer replaces existing timer`() = runTest {
        vm.setSleepTimer(5)
        vm.setSleepTimer(1)
        // Timer should be reset to 60, not 300
        assertEquals(60, vm.sleepTimerSeconds.value)
    }

    // --- setSpeed ---

    @Test fun `setSpeed emits rounded value`() {
        vm.setSpeed(1.5f)
        assertEquals(1.5f, vm.playbackSpeed.value)
    }

    @Test fun `setSpeed rounds to one decimal place`() {
        vm.setSpeed(1.25f)
        assertEquals(1.2f, vm.playbackSpeed.value)
    }

    // --- skipDeepDive ---

    @Test fun `skipDeepDive while Idle is no-op`() {
        assertEquals(DeepDiveState.Idle, vm.deepDiveState.value)
        vm.skipDeepDive()
        assertEquals(DeepDiveState.Idle, vm.deepDiveState.value)
    }

    // --- dismissDeepDiveError ---

    @Test fun `dismissDeepDiveError transitions state to Idle`() = runTest {
        // Force an error state via moreAboutThis with no chapter URL
        vm.dismissDeepDiveError()
        assertEquals(DeepDiveState.Idle, vm.deepDiveState.value)
        assertNull(vm.deepDiveChapterIndex.value)
    }

    // --- savePosition ---

    @Test fun `loadMetadata triggers updateLastPosition via episodeDao when episode downloaded`() = runTest {
        val entity = EpisodeEntity(
            audioUrl = "https://ep.mp3",
            podcastFeedUrl = "https://feed.com",
            title = "Ep",
            pubDate = 0L,
            durationSeconds = 120,
            chaptersUrl = null,
            downloadPath = "/data/ep.mp3",
            downloadStatus = "DONE"
        )
        coEvery { episodeDao.getByAudioUrl("https://ep.mp3") } returns entity
        coEvery { podcastDao.getByUrl(any()) } returns null

        vm.loadMetadata("https://ep.mp3")
        advanceTimeBy(100)

        // downloadStatus is DOWNLOADED so downloadEpisode should NOT be called
        coVerify(exactly = 0) { podcastRepo.downloadEpisode(any()) }
    }

    @Test fun `loadMetadata queues download when episode not downloaded`() = runTest {
        val entity = EpisodeEntity(
            audioUrl = "https://ep.mp3",
            podcastFeedUrl = "https://feed.com",
            title = "Ep",
            pubDate = 0L,
            durationSeconds = 120,
            chaptersUrl = null,
            downloadPath = null,
            downloadStatus = "NONE"
        )
        coEvery { episodeDao.getByAudioUrl("https://ep.mp3") } returns entity
        coEvery { podcastDao.getByUrl(any()) } returns null

        vm.loadMetadata("https://ep.mp3")
        advanceTimeBy(100)

        coVerify(exactly = 1) { podcastRepo.downloadEpisode("https://ep.mp3") }
    }
}
