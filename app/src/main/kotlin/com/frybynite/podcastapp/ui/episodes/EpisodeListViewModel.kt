package com.frybynite.podcastapp.ui.episodes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frybynite.podcastapp.data.repository.PodcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EpisodeListViewModel @Inject constructor(
    private val repo: PodcastRepository,
    savedState: SavedStateHandle
) : ViewModel() {

    private val feedUrl: String = java.net.URLDecoder.decode(
        checkNotNull(savedState["feedUrl"]), "UTF-8"
    )
    val episodes = repo.episodesForPodcast(feedUrl)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            runCatching { repo.refreshPodcast(feedUrl) }
            _isRefreshing.value = false
        }
    }

    fun downloadEpisode(audioUrl: String) {
        repo.downloadEpisode(audioUrl)
    }
}
