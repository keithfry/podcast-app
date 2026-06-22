package com.frybynite.podlore.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frybynite.podlore.data.repository.PodcastRepository
import com.frybynite.podlore.data.repository.SearchRepository
import com.frybynite.podlore.domain.model.PodcastSearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()
    data class Success(val results: List<PodcastSearchResult>) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

sealed class SubscribeState {
    object Idle : SubscribeState()
    object Loading : SubscribeState()
    object Success : SubscribeState()
    data class Error(val message: String) : SubscribeState()
}

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val podcastRepository: PodcastRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _selectedResult = MutableStateFlow<PodcastSearchResult?>(null)
    val selectedResult: StateFlow<PodcastSearchResult?> = _selectedResult.asStateFlow()

    private val _subscribeState = MutableStateFlow<SubscribeState>(SubscribeState.Idle)
    val subscribeState: StateFlow<SubscribeState> = _subscribeState.asStateFlow()

    private val _detailDescription = MutableStateFlow<String?>(null)
    val detailDescription: StateFlow<String?> = _detailDescription.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.length < 2) {
            _uiState.value = SearchUiState.Idle
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            _uiState.value = SearchUiState.Loading
            _uiState.value = runCatching { searchRepository.search(query) }
                .fold(
                    onSuccess = { SearchUiState.Success(it) },
                    onFailure = { SearchUiState.Error("Search unavailable — check your connection.") },
                )
        }
    }

    fun retry() {
        val query = _searchQuery.value
        if (query.length >= 2) onQueryChanged(query)
    }

    fun selectResult(result: PodcastSearchResult) {
        _selectedResult.value = result
        _subscribeState.value = SubscribeState.Idle
        _detailDescription.value = result.description.takeIf { !it.isNullOrBlank() }
        viewModelScope.launch {
            _detailDescription.value = searchRepository.fetchFeedDescription(result.feedUrl)
                ?.takeIf { it.isNotBlank() }
                ?: result.description
        }
    }

    fun subscribe(feedUrl: String) {
        viewModelScope.launch {
            _subscribeState.value = SubscribeState.Loading
            _subscribeState.value = runCatching { podcastRepository.addPodcast(feedUrl) }
                .fold(
                    onSuccess = { SubscribeState.Success },
                    onFailure = { SubscribeState.Error("Failed to subscribe. Check your connection.") },
                )
        }
    }
}
