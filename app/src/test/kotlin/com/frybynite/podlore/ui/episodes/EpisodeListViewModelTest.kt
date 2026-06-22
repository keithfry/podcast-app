package com.frybynite.podlore.ui.episodes

import androidx.lifecycle.SavedStateHandle
import com.frybynite.podlore.data.db.dao.PodcastDao
import com.frybynite.podlore.data.preferences.EpisodeListPreferences
import com.frybynite.podlore.data.repository.PodcastRepository
import com.frybynite.podlore.domain.model.Episode
import com.frybynite.podlore.playback.PlaybackController
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class EpisodeListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var repo: PodcastRepository
    private lateinit var podcastDao: PodcastDao
    private lateinit var episodeListPrefs: EpisodeListPreferences
    private lateinit var playbackController: PlaybackController

    private val currentlyPlayingUrlFlow = MutableStateFlow<String?>(null)
    private val isPlayingFlow = MutableStateFlow(false)

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk(relaxed = true) {
            every { episodesForPodcast(any()) } returns MutableStateFlow(emptyList())
            every { downloadProgressFlow() } returns MutableStateFlow(emptyMap())
        }
        podcastDao = mockk(relaxed = true)
        episodeListPrefs = mockk(relaxed = true) {
            every { getShowHeard(any()) } returns false
        }
        playbackController = mockk(relaxed = true) {
            every { currentlyPlayingUrl } returns currentlyPlayingUrlFlow
            every { isPlaying } returns isPlayingFlow
        }
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun makeVm(): EpisodeListViewModel = EpisodeListViewModel(
        repo = repo,
        podcastDao = podcastDao,
        episodeListPrefs = episodeListPrefs,
        playbackController = playbackController,
        savedState = SavedStateHandle(mapOf("feedUrl" to "https://feed.com"))
    )

    private fun episode(audioUrl: String) = Episode(
        audioUrl = audioUrl,
        podcastFeedUrl = "https://feed.com",
        title = "Title",
        pubDate = 0L,
        durationSeconds = 300,
        chaptersUrl = null
    )

    @Test fun `onPlayPause plays episode when nothing is currently playing`() {
        val vm = makeVm()
        currentlyPlayingUrlFlow.value = null

        vm.onPlayPause(episode("https://ep.mp3"))

        verify(exactly = 1) { playbackController.play(episode("https://ep.mp3")) }
    }

    @Test fun `onPlayPause plays episode when a different episode is playing`() {
        val vm = makeVm()
        currentlyPlayingUrlFlow.value = "https://other.mp3"

        vm.onPlayPause(episode("https://ep.mp3"))

        verify(exactly = 1) { playbackController.play(episode("https://ep.mp3")) }
    }

    @Test fun `onPlayPause pauses when tapping the currently playing episode`() {
        val vm = makeVm()
        currentlyPlayingUrlFlow.value = "https://ep.mp3"
        isPlayingFlow.value = true

        vm.onPlayPause(episode("https://ep.mp3"))

        verify(exactly = 1) { playbackController.pause() }
        verify(exactly = 0) { playbackController.play(any()) }
    }

    @Test fun `onPlayPause resumes when tapping the currently paused episode`() {
        val vm = makeVm()
        currentlyPlayingUrlFlow.value = "https://ep.mp3"
        isPlayingFlow.value = false

        vm.onPlayPause(episode("https://ep.mp3"))

        verify(exactly = 1) { playbackController.resume() }
        verify(exactly = 0) { playbackController.pause() }
        verify(exactly = 0) { playbackController.play(any()) }
    }

    @Test fun `onPlayPause switching from ep1 to ep2 calls play not pause`() {
        val vm = makeVm()
        currentlyPlayingUrlFlow.value = "https://ep1.mp3"
        isPlayingFlow.value = true

        vm.onPlayPause(episode("https://ep2.mp3"))

        verify(exactly = 1) { playbackController.play(episode("https://ep2.mp3")) }
        verify(exactly = 0) { playbackController.pause() }
    }

    @Test fun `currentlyPlayingUrl exposed from playbackController`() {
        val vm = makeVm()
        currentlyPlayingUrlFlow.value = "https://ep.mp3"

        assertEquals("https://ep.mp3", vm.currentlyPlayingUrl.value)
    }

    @Test fun `isPlaying exposed from playbackController`() {
        val vm = makeVm()
        isPlayingFlow.value = true

        assertTrue(vm.isPlaying.value)
    }

    @Test fun `refresh sets isRefreshing true then false`() = runTest {
        val vm = makeVm()

        vm.refresh()
        advanceTimeBy(100)

        assertFalse(vm.isRefreshing.value)
        coVerify(exactly = 1) { repo.refreshPodcast("https://feed.com") }
    }

    @Test fun `toggleShowHeard flips showHeard`() {
        val vm = makeVm()
        assertFalse(vm.showHeard.value)

        vm.toggleShowHeard()

        assertTrue(vm.showHeard.value)
    }

    @Test fun `setEpisodeHeard delegates to repo`() = runTest {
        val vm = makeVm()

        vm.setEpisodeHeard("https://ep.mp3", true)
        advanceTimeBy(100)

        coVerify(exactly = 1) { repo.setEpisodeHeard("https://ep.mp3", true) }
    }
}
