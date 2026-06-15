package com.frybynite.podcastapp.ui.episodes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frybynite.podcastapp.data.db.dao.PodcastDao
import com.frybynite.podcastapp.data.repository.PodcastRepository
import com.frybynite.podcastapp.domain.model.Episode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EpisodeListViewModel @Inject constructor(
    private val repo: PodcastRepository,
    private val podcastDao: PodcastDao,
    savedState: SavedStateHandle
) : ViewModel() {

    private val feedUrl: String = java.net.URLDecoder.decode(
        checkNotNull(savedState["feedUrl"]), "UTF-8"
    )

    private val _showHeard = MutableStateFlow(false)
    val showHeard: StateFlow<Boolean> = _showHeard.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val episodes: StateFlow<List<Episode>> = _showHeard
        .flatMapLatest { show ->
            repo.episodesForPodcast(feedUrl).map { list ->
                if (show) list else list.filter { !it.isHeard }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _podcastImageUrl = MutableStateFlow<String?>(null)
    val podcastImageUrl: StateFlow<String?> = _podcastImageUrl.asStateFlow()

    init {
        viewModelScope.launch {
            _podcastImageUrl.value = podcastDao.getByUrl(feedUrl)?.imageUrl
        }
    }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            runCatching { repo.refreshPodcast(feedUrl) }
            _isRefreshing.value = false
        }
    }

    fun toggleShowHeard() {
        _showHeard.value = !_showHeard.value
    }

    fun downloadEpisode(audioUrl: String) {
        repo.downloadEpisode(audioUrl)
    }

    fun setEpisodeHeard(audioUrl: String, isHeard: Boolean) {
        viewModelScope.launch { repo.setEpisodeHeard(audioUrl, isHeard) }
    }
}
