package com.frybynite.podlore.ui.podcast

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frybynite.podlore.data.db.dao.PodcastDao
import com.frybynite.podlore.data.preferences.EpisodeListPreferences
import com.frybynite.podlore.data.repository.PodcastRepository
import com.frybynite.podlore.data.repository.toDomain
import com.frybynite.podlore.domain.model.Episode
import com.frybynite.podlore.domain.model.Podcast
import com.frybynite.podlore.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

sealed class SubscribeUiState {
    object Idle : SubscribeUiState()
    object Loading : SubscribeUiState()
    data class Error(val message: String) : SubscribeUiState()
}

@HiltViewModel
class PodcastViewModel @Inject constructor(
    private val repo: PodcastRepository,
    private val podcastDao: PodcastDao,
    private val episodeListPrefs: EpisodeListPreferences,
    private val playbackController: PlaybackController,
    savedState: SavedStateHandle,
) : ViewModel() {

    val feedUrl: String = URLDecoder.decode(checkNotNull(savedState["feedUrl"]), "UTF-8")
    private val initTitle: String = savedState.get<String>("title")?.takeIf { it.isNotBlank() } ?: ""
    private val initAuthor: String = savedState.get<String>("author")?.takeIf { it.isNotBlank() } ?: ""
    private val initArtworkUrl: String? = savedState.get<String>("artworkUrl")?.takeIf { it.isNotBlank() }
    private val initDescription: String? = savedState.get<String>("description")?.takeIf { it.isNotBlank() }

    val podcast: StateFlow<Podcast?> = podcastDao.observeByUrl(feedUrl)
        .map { it?.toDomain() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isSubscribed: StateFlow<Boolean> = podcast
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val displayTitle: StateFlow<String> = podcast
        .map { it?.title?.takeIf { t -> t.isNotBlank() } ?: initTitle }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initTitle)

    val displayAuthor: StateFlow<String> = podcast
        .map { it?.author?.takeIf { a -> a.isNotBlank() } ?: initAuthor }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initAuthor)

    val displayArtworkUrl: StateFlow<String?> = podcast
        .map { it?.imageUrl ?: initArtworkUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initArtworkUrl)

    val displayDescription: StateFlow<String?> = podcast
        .map { it?.description?.takeIf { d -> d.isNotBlank() } ?: initDescription }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initDescription)

    private val _subscribeState = MutableStateFlow<SubscribeUiState>(SubscribeUiState.Idle)
    val subscribeState: StateFlow<SubscribeUiState> = _subscribeState.asStateFlow()

    private val _showUnsubscribeConfirm = MutableStateFlow(false)
    val showUnsubscribeConfirm: StateFlow<Boolean> = _showUnsubscribeConfirm.asStateFlow()

    val episodes: StateFlow<List<Episode>> = repo.episodesForPodcast(feedUrl)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadProgress: StateFlow<Map<String, Float>> = repo.downloadProgressFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _showHeard = MutableStateFlow(episodeListPrefs.getShowHeard(feedUrl))
    val showHeard: StateFlow<Boolean> = _showHeard.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val currentlyPlayingUrl: StateFlow<String?> = playbackController.currentlyPlayingUrl
    val isPlaying: StateFlow<Boolean> = playbackController.isPlaying

    fun subscribe() {
        viewModelScope.launch {
            _subscribeState.value = SubscribeUiState.Loading
            _subscribeState.value = runCatching { repo.addPodcast(feedUrl) }
                .fold(
                    onSuccess = { SubscribeUiState.Idle },
                    onFailure = { SubscribeUiState.Error("Failed to subscribe. Check your connection.") },
                )
        }
    }

    fun requestUnsubscribe() { _showUnsubscribeConfirm.value = true }
    fun dismissUnsubscribe() { _showUnsubscribeConfirm.value = false }

    fun confirmUnsubscribe() {
        _showUnsubscribeConfirm.value = false
        viewModelScope.launch {
            _subscribeState.value = SubscribeUiState.Loading
            runCatching { repo.removePodcastCompletely(feedUrl) }
                .onFailure { _subscribeState.value = SubscribeUiState.Error("Failed to unsubscribe.") }
                .onSuccess { _subscribeState.value = SubscribeUiState.Idle }
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
