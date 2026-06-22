package com.frybynite.podlore.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppStateTest {

    private fun state(currentUrl: String? = null) =
        AppState(currentlyPlayingUrl = MutableStateFlow(currentUrl))

    @Test fun `showMiniPlayer false when no episode playing`() = runTest {
        val s = state(currentUrl = null)
        assertFalse(s.showMiniPlayer.value)
    }

    @Test fun `showMiniPlayer true when episode url present`() = runTest {
        val s = state(currentUrl = "https://ep.mp3")
        assertTrue(s.showMiniPlayer.value)
    }

    @Test fun `showMiniPlayer updates when url changes`() = runTest {
        val url = MutableStateFlow<String?>(null)
        val s = AppState(currentlyPlayingUrl = url)

        assertFalse(s.showMiniPlayer.value)
        url.value = "https://ep.mp3"
        assertTrue(s.showMiniPlayer.value)
        url.value = null
        assertFalse(s.showMiniPlayer.value)
    }

    @Test fun `isPlayerSheetOpen starts false`() {
        assertFalse(state().isPlayerSheetOpen.value)
    }

    @Test fun `openPlayer sets isPlayerSheetOpen true`() {
        val s = state()
        s.openPlayer()
        assertTrue(s.isPlayerSheetOpen.value)
    }

    @Test fun `closePlayer sets isPlayerSheetOpen false`() {
        val s = state()
        s.openPlayer()
        s.closePlayer()
        assertFalse(s.isPlayerSheetOpen.value)
    }

    @Test fun `closePlayer when already closed stays false`() {
        val s = state()
        s.closePlayer()
        assertFalse(s.isPlayerSheetOpen.value)
    }
}
