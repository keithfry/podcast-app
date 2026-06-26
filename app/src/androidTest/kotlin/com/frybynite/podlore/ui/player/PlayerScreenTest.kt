package com.frybynite.podlore.ui.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frybynite.podlore.data.db.dao.DeepDiveDao
import com.frybynite.podlore.data.db.dao.EpisodeDao
import com.frybynite.podlore.data.db.dao.PodcastDao
import com.frybynite.podlore.data.preferences.SpeedPreferences
import com.frybynite.podlore.data.repository.ChapterRepository
import com.frybynite.podlore.data.repository.PodcastRepository
import com.frybynite.podlore.data.repository.TranscriptRepository
import com.frybynite.podlore.deepdive.DeepDiveOrchestrator
import com.frybynite.podlore.deepdive.ModelDownloadManager
import com.frybynite.podlore.deepdive.ModelDownloadState
import com.frybynite.podlore.deepdive.TextSummarizer
import com.frybynite.podlore.playback.PlaybackController
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private fun makeVm(): PlayerViewModel {
        val deepDiveDao = mockk<DeepDiveDao>(relaxed = true) {
            every { flowForEpisode(any()) } returns emptyFlow()
        }
        val modelDownloadManager = mockk<ModelDownloadManager>(relaxed = true) {
            every { state } returns MutableStateFlow(ModelDownloadState.Idle)
        }
        return PlayerViewModel(
            context = mockk(relaxed = true),
            chapterRepo = mockk(relaxed = true) {
                every { chaptersForEpisode(any()) } returns emptyFlow()
            },
            episodeDao = mockk(relaxed = true),
            podcastDao = mockk(relaxed = true),
            podcastRepo = mockk(relaxed = true),
            deepDiveDao = deepDiveDao,
            speedPrefs = mockk<SpeedPreferences>(relaxed = true) { every { speed } returns 1f },
            deepDiveOrchestrator = mockk(relaxed = true),
            summarizer = mockk<TextSummarizer>(relaxed = true) { every { isModelAvailable() } returns false },
            modelDownloadManager = modelDownloadManager,
            transcriptRepo = mockk(relaxed = true),
            playbackController = mockk(relaxed = true),
        )
    }

    @Test fun playButtonShownInitially() {
        val vm = makeVm()
        composeRule.setContent {
            PlayerScreen(audioUrl = "https://ep.mp3", onDismiss = {}, vm = vm)
        }
        composeRule.onNodeWithContentDescription("Play").assertIsDisplayed()
    }

    @Test fun speedLabelShows1x0Initially() {
        val vm = makeVm()
        composeRule.setContent {
            PlayerScreen(audioUrl = "https://ep.mp3", onDismiss = {}, vm = vm)
        }
        composeRule.onNodeWithText("1.0×").assertIsDisplayed()
    }

    @Test fun sleepTimerTextNotShownInitially() {
        val vm = makeVm()
        composeRule.setContent {
            PlayerScreen(audioUrl = "https://ep.mp3", onDismiss = {}, vm = vm)
        }
        composeRule.onNodeWithText("5:00").assertDoesNotExist()
    }

    @Test fun speedUpdatesTo1x5AfterSetSpeed() {
        val vm = makeVm()
        composeRule.setContent {
            PlayerScreen(audioUrl = "https://ep.mp3", onDismiss = {}, vm = vm)
        }
        vm.setSpeed(1.5f)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1.5×").assertIsDisplayed()
    }

    @Test fun sleepTimerTextAppearsAfterSetSleepTimer() {
        val vm = makeVm()
        composeRule.setContent {
            PlayerScreen(audioUrl = "https://ep.mp3", onDismiss = {}, vm = vm)
        }
        vm.setSleepTimer(5)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("5:00").assertIsDisplayed()
    }
}
