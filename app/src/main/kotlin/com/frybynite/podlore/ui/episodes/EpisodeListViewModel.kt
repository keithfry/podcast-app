package com.frybynite.podlore.ui.episodes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frybynite.podlore.data.db.dao.PodcastDao
import com.frybynite.podlore.data.preferences.EpisodeListPreferences
import com.frybynite.podlore.data.repository.PodcastRepository
import com.frybynite.podlore.domain.model.Episode
import com.frybynite.podlore.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EpisodeListViewModel @Inject constructor(
    private val repo: PodcastRepository,
    private val podcastDao: PodcastDao,
    private val episodeListPrefs: EpisodeListPreferences,
    private val playbackController: PlaybackController,
    savedState: SavedStateHandle
) : ViewModel() {

    private val feedUrl: String = java.net.URLDecoder.decode(
        checkNotNull(savedState["feedUrl"]), "UTF-8"
    )

    private val _showHeard = MutableStateFlow(episodeListPrefs.getShowHeard(feedUrl))
    val showHeard: StateFlow<Boolean> = _showHeard.asStateFlow()

    val episodes: StateFlow<List<Episode>> = repo.episodesForPodcast(feedUrl)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadProgress: StateFlow<Map<String, Float>> = repo.downloadProgressFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _podcastImageUrl = MutableStateFlow<String?>(null)
    val podcastImageUrl: StateFlow<String?> = _podcastImageUrl.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val currentlyPlayingUrl: StateFlow<String?> = playbackController.currentlyPlayingUrl
    val isPlaying: StateFlow<Boolean> = playbackController.isPlaying

    init {
        viewModelScope.launch {
            _podcastImageUrl.value = podcastDao.getByUrl(feedUrl)?.imageUrl
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            runCatching { repo.refreshPodcast(feedUrl) }
            _isRefreshing.value = false
        }
    }

    fun toggleShowHeard() {
        val next = !_showHeard.value
        _showHeard.value = next
        episodeListPrefs.setShowHeard(feedUrl, next)
    }

    fun onPlayPause(episode: Episode) {
        if (playbackController.currentlyPlayingUrl.value == episode.audioUrl) {
            if (playbackController.isPlaying.value) playbackController.pause()
            else playbackController.resume()
        } else {
            playbackController.play(episode)
        }
    }

    fun setEpisodeHeard(audioUrl: String, isHeard: Boolean) {
        viewModelScope.launch { repo.setEpisodeHeard(audioUrl, isHeard) }
    }
}
