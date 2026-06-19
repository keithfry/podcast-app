package com.frybynite.podcastapp.ui

import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppState(currentlyPlayingUrl: StateFlow<String?>) {

    val showMiniPlayer: StateFlow<Boolean> = object : StateFlow<Boolean> {
        override val value get() = currentlyPlayingUrl.value != null
        override val replayCache get() = listOf(value)
        override suspend fun collect(collector: FlowCollector<Boolean>) =
            currentlyPlayingUrl.collect { collector.emit(it != null) }
    }

    private val _isPlayerSheetOpen = MutableStateFlow(false)
    val isPlayerSheetOpen: StateFlow<Boolean> = _isPlayerSheetOpen.asStateFlow()

    fun openPlayer() { _isPlayerSheetOpen.value = true }
    fun closePlayer() { _isPlayerSheetOpen.value = false }
}
