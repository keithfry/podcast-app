package com.frybynite.podcastapp.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.frybynite.podcastapp.data.db.dao.EpisodeDao
import com.frybynite.podcastapp.data.preferences.SpeedPreferences
import com.frybynite.podcastapp.data.repository.ChapterRepository
import com.frybynite.podcastapp.data.repository.PodcastRepository
import com.frybynite.podcastapp.domain.model.DownloadStatus
import com.frybynite.podcastapp.domain.model.Episode
import com.frybynite.podcastapp.service.PlaybackService
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

@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val episodeDao: EpisodeDao,
    private val podcastRepo: PodcastRepository,
    private val chapterRepo: ChapterRepository,
    private val speedPrefs: SpeedPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private val _currentlyPlayingUrl = MutableStateFlow<String?>(null)
    val currentlyPlayingUrl: StateFlow<String?> = _currentlyPlayingUrl.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _pendingAutoPlayUrl = MutableStateFlow<String?>(null)

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching {
                val ctrl = future.get()
                _controller.value = ctrl
                _isPlaying.value = ctrl.isPlaying
                _currentlyPlayingUrl.value = ctrl.currentMediaItem?.mediaId
                ctrl.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                    }
                    override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                        _currentlyPlayingUrl.value = item?.mediaId
                        val audioUrl = item?.mediaId ?: return
                        scope.launch {
                            val entity = episodeDao.getByAudioUrl(audioUrl) ?: return@launch
                            entity.chaptersUrl?.let { url ->
                                chapterRepo.fetchAndCacheChapters(audioUrl, url)
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
    fun resume() { _controller.value?.play() }

    private fun MediaController.applySpeed() {
        setPlaybackParameters(PlaybackParameters(speedPrefs.speed))
    }
}
