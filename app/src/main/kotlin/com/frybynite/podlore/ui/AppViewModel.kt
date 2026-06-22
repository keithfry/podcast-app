package com.frybynite.podlore.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frybynite.podlore.data.db.dao.EpisodeDao
import com.frybynite.podlore.data.db.dao.PodcastDao
import com.frybynite.podlore.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val episodeDao: EpisodeDao,
    private val podcastDao: PodcastDao,
) : ViewModel() {
    val currentlyPlayingUrl: StateFlow<String?> = playbackController.currentlyPlayingUrl
    val currentTitle: StateFlow<String?> = playbackController.currentTitle
    val isPlaying: StateFlow<Boolean> = playbackController.isPlaying

    val currentArtworkUrl: StateFlow<String?> = playbackController.currentlyPlayingUrl
        .flatMapLatest { audioUrl ->
            flow {
                if (audioUrl == null) { emit(null); return@flow }
                val episode = episodeDao.getByAudioUrl(audioUrl)
                val podcast = episode?.podcastFeedUrl?.let { podcastDao.getByUrl(it) }
                emit(episode?.imageUrl ?: podcast?.imageUrl)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun pause() = playbackController.pause()
    fun resume() = playbackController.resume()
}
