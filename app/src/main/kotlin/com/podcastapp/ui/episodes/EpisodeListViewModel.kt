package com.podcastapp.ui.episodes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcastapp.data.repository.PodcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EpisodeListViewModel @Inject constructor(
    private val repo: PodcastRepository,
    savedState: SavedStateHandle
) : ViewModel() {

    private val feedUrl: String = checkNotNull(savedState["feedUrl"])
    val episodes = repo.episodesForPodcast(feedUrl)

    fun refresh() {
        viewModelScope.launch { runCatching { repo.refreshPodcast(feedUrl) } }
    }
}
