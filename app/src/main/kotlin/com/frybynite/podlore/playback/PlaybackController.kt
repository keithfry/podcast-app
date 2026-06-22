package com.frybynite.podlore.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.frybynite.podlore.data.db.dao.EpisodeDao
import com.frybynite.podlore.data.preferences.SpeedPreferences
import com.frybynite.podlore.data.repository.ChapterRepository
import com.frybynite.podlore.data.repository.PodcastRepository
import com.frybynite.podlore.domain.model.DownloadStatus
import com.frybynite.podlore.domain.model.Episode
import com.frybynite.podlore.service.PlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "playback_state"
private const val PREF_LAST_URL = "last_url"

@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val episodeDao: EpisodeDao,
    private val podcastRepo: PodcastRepository,
    private val chapterRepo: ChapterRepository,
    private val speedPrefs: SpeedPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private val _currentlyPlayingUrl = MutableStateFlow<String?>(null)
    val currentlyPlayingUrl: StateFlow<String?> = _currentlyPlayingUrl.asStateFlow()

    private val _currentTitle = MutableStateFlow<String?>(null)
    val currentTitle: StateFlow<String?> = _currentTitle.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _pendingAutoPlayUrl = MutableStateFlow<String?>(null)

    init {
        // Restore last-playing episode on cold start so mini player reappears
        prefs.getString(PREF_LAST_URL, null)?.let { savedUrl ->
            _currentlyPlayingUrl.value = savedUrl
            scope.launch {
                _currentTitle.value = episodeDao.getByAudioUrl(savedUrl)?.title
            }
        }

        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching {
                val ctrl = future.get()
                _controller.value = ctrl
                _isPlaying.value = ctrl.isPlaying
                // If controller already has an item (service survived), prefer it over prefs
                ctrl.currentMediaItem?.let { item ->
                    _currentlyPlayingUrl.value = item.mediaId
                    _currentTitle.value = item.mediaMetadata.title?.toString()
                }
                ctrl.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            clearCurrentEpisode()
                        }
                    }
                    override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                        if (item == null) return
                        _currentlyPlayingUrl.value = item.mediaId
                        _currentTitle.value = item.mediaMetadata.title?.toString()
                        saveCurrentUrl(item.mediaId)
                        scope.launch {
                            val entity = episodeDao.getByAudioUrl(item.mediaId) ?: return@launch
                            entity.chaptersUrl?.let { url ->
                                chapterRepo.fetchAndCacheChapters(item.mediaId, url)
                            }
                        }
                    }
                })
            }
        }, { it.run() })

        scope.launch {
            combine(_pendingAutoPlayUrl.filterNotNull(), _controller.filterNotNull()) { pending, ctrl ->
                pending to ctrl
            }.collectLatest { (pending, ctrl) ->
                val entity = episodeDao.getByAudioUrlFlow(pending).first { entity ->
                    entity == null ||
                        (entity.downloadStatus == "DONE" && entity.downloadPath != null) ||
                        entity.downloadStatus == "NONE"
                }
                _pendingAutoPlayUrl.value = null
                if (entity?.downloadStatus == "DONE" && entity.downloadPath != null) {
                    val item = MediaItem.Builder()
                        .setMediaId(entity.audioUrl)
                        .setUri(Uri.fromFile(File(entity.downloadPath)))
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(entity.title).build())
                        .build()
                    ctrl.applySpeed()
                    ctrl.setMediaItem(item)
                    ctrl.prepare()
                    ctrl.play()
                }
            }
        }
    }

    fun play(episode: Episode) {
        when (episode.downloadStatus) {
            DownloadStatus.DONE -> {
                val path = episode.downloadPath ?: return
                val item = MediaItem.Builder()
                    .setMediaId(episode.audioUrl)
                    .setUri(Uri.fromFile(File(path)))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(episode.title).build())
                    .build()
                _controller.value?.let { c ->
                    c.applySpeed()
                    c.setMediaItem(item)
                    c.prepare()
                    c.play()
                }
            }
            DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                _pendingAutoPlayUrl.value = episode.audioUrl
            }
            DownloadStatus.NONE -> {
                _pendingAutoPlayUrl.value?.let { podcastRepo.cancelDownload(it) }
                _pendingAutoPlayUrl.value = episode.audioUrl
                podcastRepo.downloadEpisode(episode.audioUrl)
            }
        }
    }

    fun pause() { _controller.value?.pause() }

    fun resume() {
        val ctrl = _controller.value ?: return
        if (ctrl.currentMediaItem != null) {
            ctrl.play()
            return
        }
        // Cold-start resume: load saved episode at saved position
        val savedUrl = _currentlyPlayingUrl.value ?: return
        scope.launch {
            val entity = episodeDao.getByAudioUrl(savedUrl) ?: return@launch
            val uri = entity.downloadPath?.let { Uri.fromFile(File(it)) }
                ?: Uri.parse(entity.audioUrl)
            val item = MediaItem.Builder()
                .setMediaId(entity.audioUrl)
                .setUri(uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(entity.title).build())
                .build()
            ctrl.applySpeed()
            ctrl.setMediaItem(item, entity.lastPositionMs)
            ctrl.prepare()
            ctrl.play()
        }
    }

    private fun saveCurrentUrl(url: String) {
        prefs.edit().putString(PREF_LAST_URL, url).apply()
    }

    private fun clearCurrentEpisode() {
        _currentlyPlayingUrl.value = null
        _currentTitle.value = null
        prefs.edit().remove(PREF_LAST_URL).apply()
    }

    private fun MediaController.applySpeed() {
        setPlaybackParameters(PlaybackParameters(speedPrefs.speed))
    }
}
