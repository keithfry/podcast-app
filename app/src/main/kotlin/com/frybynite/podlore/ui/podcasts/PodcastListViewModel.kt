package com.frybynite.podlore.ui.podcasts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frybynite.podlore.data.repository.PodcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PodcastListViewModel @Inject constructor(
    private val repo: PodcastRepository
) : ViewModel() {

    val podcasts = repo.podcasts

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun addPodcast(url: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching { repo.addPodcast(url) }
                .onFailure { _error.value = it.message ?: "Unknown error" }
            _isLoading.value = false
        }
    }

    fun removePodcast(feedUrl: String) {
        viewModelScope.launch { repo.removePodcast(feedUrl) }
    }

    fun dismissError() { _error.value = null }
}
