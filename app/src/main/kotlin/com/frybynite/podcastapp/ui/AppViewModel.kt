package com.frybynite.podcastapp.ui

import androidx.lifecycle.ViewModel
import com.frybynite.podcastapp.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val playbackController: PlaybackController,
) : ViewModel() {
    val currentlyPlayingUrl: StateFlow<String?> = playbackController.currentlyPlayingUrl
    val currentTitle: StateFlow<String?> = playbackController.currentTitle
    val isPlaying: StateFlow<Boolean> = playbackController.isPlaying

    fun pause() = playbackController.pause()
    fun resume() = playbackController.resume()
}
