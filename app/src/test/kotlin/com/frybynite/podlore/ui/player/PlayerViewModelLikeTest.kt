package com.frybynite.podlore.ui.player

import com.frybynite.podlore.data.db.dao.DeepDiveDao
import com.frybynite.podlore.data.db.dao.EpisodeDao
import com.frybynite.podlore.data.db.dao.PodcastDao
import com.frybynite.podlore.data.db.entities.EpisodeEntity
import com.frybynite.podlore.data.preferences.SpeedPreferences
import com.frybynite.podlore.data.repository.ChapterRepository
import com.frybynite.podlore.data.repository.PodcastRepository
import com.frybynite.podlore.data.repository.TranscriptRepository
import com.frybynite.podlore.deepdive.DeepDiveOrchestrator
import com.frybynite.podlore.deepdive.ModelDownloadManager
import com.frybynite.podlore.deepdive.ModelDownloadState
import com.frybynite.podlore.deepdive.TextSummarizer
import com.frybynite.podlore.playback.PlaybackController
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelLikeTest {

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
            modelDownloadManager = modelDownloadManager,
            transcriptRepo = mockk(relaxed = true),
            playbackController = mockk(relaxed = true) {
                every { controller } returns MutableStateFlow(null)
                every { currentlyPlayingUrl } returns MutableStateFlow(null)
                every { isPlaying } returns MutableStateFlow(false)
            }
        )
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun entity(audioUrl: String, isLiked: Boolean = false) = EpisodeEntity(
        audioUrl = audioUrl,
        podcastFeedUrl = "https://feed.com",
        title = "Title",
        pubDate = 0L,
        durationSeconds = 300,
        chaptersUrl = null,
        downloadPath = "/data/ep.mp3",
        downloadStatus = "DONE",
        isLiked = isLiked
    )

    @Test fun `isLiked defaults to false`() {
        assertFalse(vm.isLiked.value)
    }

    @Test fun `loadMetadata sets isLiked true from entity`() = runTest {
        coEvery { episodeDao.getByAudioUrl("https://ep.mp3") } returns entity("https://ep.mp3", isLiked = true)
        coEvery { podcastDao.getByUrl(any()) } returns null

        vm.loadMetadata("https://ep.mp3")
        advanceTimeBy(100)

        assertTrue(vm.isLiked.value)
    }

    @Test fun `loadMetadata leaves isLiked false when entity has isLiked false`() = runTest {
        coEvery { episodeDao.getByAudioUrl("https://ep.mp3") } returns entity("https://ep.mp3", isLiked = false)
        coEvery { podcastDao.getByUrl(any()) } returns null

        vm.loadMetadata("https://ep.mp3")
        advanceTimeBy(100)

        assertFalse(vm.isLiked.value)
    }

    @Test fun `loadMetadata resets isLiked synchronously when switching episodes`() = runTest {
        coEvery { episodeDao.getByAudioUrl("https://ep1.mp3") } returns entity("https://ep1.mp3", isLiked = true)
        coEvery { episodeDao.getByAudioUrl("https://ep2.mp3") } returns entity("https://ep2.mp3", isLiked = false)
        coEvery { podcastDao.getByUrl(any()) } returns null

        vm.loadMetadata("https://ep1.mp3")
        advanceTimeBy(100)
        assertTrue(vm.isLiked.value)

        vm.loadMetadata("https://ep2.mp3")
        // No advanceTimeBy — synchronous reset should have cleared isLiked already
        assertFalse(vm.isLiked.value)
    }

    @Test fun `toggleLike flips isLiked from false to true`() = runTest {
        coEvery { episodeDao.getByAudioUrl("https://ep.mp3") } returns entity("https://ep.mp3", isLiked = false)
        coEvery { podcastDao.getByUrl(any()) } returns null

        vm.loadMetadata("https://ep.mp3")
        advanceTimeBy(100)

        vm.toggleLike()

        assertTrue(vm.isLiked.value)
    }

    @Test fun `toggleLike flips isLiked from true to false`() = runTest {
        coEvery { episodeDao.getByAudioUrl("https://ep.mp3") } returns entity("https://ep.mp3", isLiked = true)
        coEvery { podcastDao.getByUrl(any()) } returns null

        vm.loadMetadata("https://ep.mp3")
        advanceTimeBy(100)

        vm.toggleLike()

        assertFalse(vm.isLiked.value)
    }

    @Test fun `toggleLike calls episodeDao updateIsLiked with correct audioUrl and new value`() = runTest {
        coEvery { episodeDao.getByAudioUrl("https://ep.mp3") } returns entity("https://ep.mp3", isLiked = false)
        coEvery { podcastDao.getByUrl(any()) } returns null

        vm.loadMetadata("https://ep.mp3")
        advanceTimeBy(100)

        vm.toggleLike()
        advanceTimeBy(100)

        coVerify(exactly = 1) { episodeDao.updateIsLiked("https://ep.mp3", true) }
    }

    @Test fun `toggleLike is no-op when no episode loaded`() = runTest {
        vm.toggleLike()
        advanceTimeBy(100)

        coVerify(exactly = 0) { episodeDao.updateIsLiked(any(), any()) }
    }

    @Test fun `repeated toggleLike alternates isLiked state`() = runTest {
        coEvery { episodeDao.getByAudioUrl("https://ep.mp3") } returns entity("https://ep.mp3", isLiked = false)
        coEvery { podcastDao.getByUrl(any()) } returns null

        vm.loadMetadata("https://ep.mp3")
        advanceTimeBy(100)

        // false -> true -> false -> true
        vm.toggleLike()
        vm.toggleLike()
        vm.toggleLike()

        assertTrue(vm.isLiked.value)
    }
}
