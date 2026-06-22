package com.frybynite.podlore.ui.player

import com.frybynite.podlore.data.db.dao.DeepDiveDao
import com.frybynite.podlore.data.db.dao.EpisodeDao
import com.frybynite.podlore.data.db.dao.PodcastDao
import com.frybynite.podlore.data.preferences.SpeedPreferences
import com.frybynite.podlore.data.repository.ChapterRepository
import com.frybynite.podlore.data.repository.PodcastRepository
import com.frybynite.podlore.data.repository.TranscriptRepository
import com.frybynite.podlore.deepdive.DeepDiveOrchestrator
import com.frybynite.podlore.deepdive.ModelDownloadManager
import com.frybynite.podlore.deepdive.TextSummarizer
import com.frybynite.podlore.playback.PlaybackController
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelPositionTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `currentPositionMs defaults to zero`() {
        val vm = PlayerViewModel(
            context = mockk(relaxed = true),
            chapterRepo = mockk(relaxed = true),
            episodeDao = mockk(relaxed = true),
            podcastDao = mockk(relaxed = true),
            speedPrefs = mockk(relaxed = true) { io.mockk.every { speed } returns 1f },
            deepDiveOrchestrator = mockk(relaxed = true),
            summarizer = mockk(relaxed = true),
            podcastRepo = mockk(relaxed = true),
            deepDiveDao = mockk(relaxed = true),
            modelDownloadManager = mockk(relaxed = true),
            transcriptRepo = mockk(relaxed = true),
            playbackController = mockk(relaxed = true) {
                every { controller } returns MutableStateFlow(null)
                every { currentlyPlayingUrl } returns MutableStateFlow(null)
                every { isPlaying } returns MutableStateFlow(false)
            }
        )
        assertEquals(0L, vm.currentPositionMs.value)
    }

    @Test fun `durationMs defaults to zero`() {
        val vm = PlayerViewModel(
            context = mockk(relaxed = true),
            chapterRepo = mockk(relaxed = true),
            episodeDao = mockk(relaxed = true),
            podcastDao = mockk(relaxed = true),
            speedPrefs = mockk(relaxed = true) { io.mockk.every { speed } returns 1f },
            deepDiveOrchestrator = mockk(relaxed = true),
            summarizer = mockk(relaxed = true),
            podcastRepo = mockk(relaxed = true),
            deepDiveDao = mockk(relaxed = true),
            modelDownloadManager = mockk(relaxed = true),
            transcriptRepo = mockk(relaxed = true),
            playbackController = mockk(relaxed = true) {
                every { controller } returns MutableStateFlow(null)
                every { currentlyPlayingUrl } returns MutableStateFlow(null)
                every { isPlaying } returns MutableStateFlow(false)
            }
        )
        assertEquals(0L, vm.durationMs.value)
    }
}
