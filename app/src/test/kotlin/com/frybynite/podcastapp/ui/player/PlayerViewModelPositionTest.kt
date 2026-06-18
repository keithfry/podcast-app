package com.frybynite.podcastapp.ui.player

import com.frybynite.podcastapp.data.db.dao.DeepDiveDao
import com.frybynite.podcastapp.data.db.dao.EpisodeDao
import com.frybynite.podcastapp.data.db.dao.PodcastDao
import com.frybynite.podcastapp.data.preferences.SpeedPreferences
import com.frybynite.podcastapp.data.repository.ChapterRepository
import com.frybynite.podcastapp.data.repository.PodcastRepository
import com.frybynite.podcastapp.data.repository.TranscriptRepository
import com.frybynite.podcastapp.deepdive.DeepDiveOrchestrator
import com.frybynite.podcastapp.deepdive.ModelDownloadManager
import com.frybynite.podcastapp.deepdive.TextSummarizer
import com.frybynite.podcastapp.playback.PlaybackController
import io.mockk.mockk
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
            playbackController = mockk(relaxed = true)
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
            playbackController = mockk(relaxed = true)
        )
        assertEquals(0L, vm.durationMs.value)
    }
}
