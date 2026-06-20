package com.frybynite.podcastapp.ui

import com.frybynite.podcastapp.data.db.dao.EpisodeDao
import com.frybynite.podcastapp.data.db.dao.PodcastDao
import com.frybynite.podcastapp.playback.PlaybackController
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test

class AppViewModelTest {

    private val playbackController = mockk<PlaybackController>(relaxed = true) {
        every { currentlyPlayingUrl } returns MutableStateFlow(null)
        every { currentTitle } returns MutableStateFlow(null)
        every { isPlaying } returns MutableStateFlow(false)
    }
    private val episodeDao = mockk<EpisodeDao>(relaxed = true)
    private val podcastDao = mockk<PodcastDao>(relaxed = true)

    private val vm = AppViewModel(playbackController, episodeDao, podcastDao)

    @Test fun `pause delegates to playbackController`() {
        vm.pause()
        verify(exactly = 1) { playbackController.pause() }
    }

    @Test fun `resume delegates to playbackController`() {
        vm.resume()
        verify(exactly = 1) { playbackController.resume() }
    }

    @Test fun `pause does not call resume`() {
        vm.pause()
        verify(exactly = 0) { playbackController.resume() }
    }

    @Test fun `resume does not call pause`() {
        vm.resume()
        verify(exactly = 0) { playbackController.pause() }
    }
}
