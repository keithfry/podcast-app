package com.frybynite.podcastapp.ui.episodes

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.frybynite.podcastapp.data.db.dao.PodcastDao
import com.frybynite.podcastapp.data.preferences.EpisodeListPreferences
import com.frybynite.podcastapp.data.repository.PodcastRepository
import com.frybynite.podcastapp.domain.model.DownloadStatus
import com.frybynite.podcastapp.domain.model.Episode
import com.frybynite.podcastapp.service.PlaybackService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class EpisodeListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: PodcastRepository,
    private val podcastDao: PodcastDao,
    private val episodeListPrefs: EpisodeListPreferences,
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

    private val _currentlyPlayingUrl = MutableStateFlow<String?>(null)
    val currentlyPlayingUrl: StateFlow<String?> = _currentlyPlayingUrl.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var pendingAutoPlayUrl: String? = null
    private var controller: MediaController? = null

    init {
        viewModelScope.launch {
            _podcastImageUrl.value = podcastDao.getByUrl(feedUrl)?.imageUrl
        }

        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching {
                controller = future.get()
                controller?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                    }
                    override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                        _currentlyPlayingUrl.value = item?.mediaId
                    }
                })
                // Sync initial state in case service is already playing
                controller?.let { c ->
                    _isPlaying.value = c.isPlaying
                    _currentlyPlayingUrl.value = c.currentMediaItem?.mediaId
                }
            }
        }, { it.run() })

        viewModelScope.launch {
            episodes.collect { list ->
                val pending = pendingAutoPlayUrl ?: return@collect
                val ep = list.find { it.audioUrl == pending } ?: return@collect
                if (ep.downloadStatus == DownloadStatus.DONE && ep.downloadPath != null) {
                    pendingAutoPlayUrl = null
                    val item = MediaItem.Builder()
                        .setMediaId(ep.audioUrl)
                        .setUri(Uri.fromFile(File(ep.downloadPath)))
                        .build()
                    controller?.setMediaItem(item)
                    controller?.prepare()
                    controller?.play()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        controller?.release()
        controller = null
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
        val ctrl = controller ?: return
        if (_currentlyPlayingUrl.value == episode.audioUrl) {
            if (_isPlaying.value) ctrl.pause() else ctrl.play()
            return
        }
        when (episode.downloadStatus) {
            DownloadStatus.DONE -> {
                val path = episode.downloadPath ?: return
                val item = MediaItem.Builder()
                    .setMediaId(episode.audioUrl)
                    .setUri(Uri.fromFile(File(path)))
                    .build()
                ctrl.setMediaItem(item)
                ctrl.prepare()
                ctrl.play()
            }
            DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                pendingAutoPlayUrl = episode.audioUrl
            }
            DownloadStatus.NONE -> {
                pendingAutoPlayUrl?.let { repo.cancelDownload(it) }
                pendingAutoPlayUrl = episode.audioUrl
                repo.downloadEpisode(episode.audioUrl)
            }
        }
    }

    fun setEpisodeHeard(audioUrl: String, isHeard: Boolean) {
        viewModelScope.launch { repo.setEpisodeHeard(audioUrl, isHeard) }
    }
}
