package com.frybynite.podcastapp.ui.player

import com.frybynite.podcastapp.data.db.dao.EpisodeDao
import com.frybynite.podcastapp.data.db.dao.PodcastDao
import com.frybynite.podcastapp.data.preferences.SpeedPreferences
import com.frybynite.podcastapp.data.repository.ChapterRepository
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
            speedPrefs = mockk(relaxed = true) { io.mockk.every { speed } returns 1f }
        )
        assertEquals(0L, vm.currentPositionMs.value)
    }

    @Test fun `durationMs defaults to zero`() {
        val vm = PlayerViewModel(
            context = mockk(relaxed = true),
            chapterRepo = mockk(relaxed = true),
            episodeDao = mockk(relaxed = true),
            podcastDao = mockk(relaxed = true),
            speedPrefs = mockk(relaxed = true) { io.mockk.every { speed } returns 1f }
        )
        assertEquals(0L, vm.durationMs.value)
    }
}
