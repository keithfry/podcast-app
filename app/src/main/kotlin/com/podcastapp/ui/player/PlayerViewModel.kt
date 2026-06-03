package com.podcastapp.ui.player

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.media3.common.PlaybackParameters
import com.podcastapp.data.db.dao.EpisodeDao
import com.podcastapp.data.db.dao.PodcastDao
import com.podcastapp.data.preferences.SpeedPreferences
import com.podcastapp.data.repository.ChapterRepository
import com.podcastapp.data.repository.toDomain
import com.podcastapp.domain.model.Chapter
import com.podcastapp.domain.model.Episode
import com.podcastapp.service.PlaybackService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chapterRepo: ChapterRepository,
    private val episodeDao: EpisodeDao,
    private val podcastDao: PodcastDao,
    private val speedPrefs: SpeedPreferences
) : ViewModel() {

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()

    private val _currentChapterIndex = MutableStateFlow(0)
    val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(speedPrefs.speed)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _podcastImageUrl = MutableStateFlow<String?>(null)

    // Sleep timer: remaining seconds, null = not active
    private val _sleepTimerSeconds = MutableStateFlow<Int?>(null)
    val sleepTimerSeconds: StateFlow<Int?> = _sleepTimerSeconds.asStateFlow()
    private var sleepTimerJob: Job? = null
    val podcastImageUrl: StateFlow<String?> = _podcastImageUrl.asStateFlow()

    private var chaptersJob: Job? = null

    var controller: MediaController? = null
        private set

    fun connect(audioUrl: String) {
        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching {
                controller = future.get()
                controller?.setPlaybackParameters(PlaybackParameters(speedPrefs.speed))
                controller?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                    }
                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int
                    ) {
                        updateCurrentChapterIndex()
                    }
                })
                loadAndPlay(audioUrl)
            }.onFailure { /* controller stays null; UI handles gracefully */ }
        }, context.mainExecutor)
    }

    fun playEpisode(episode: Episode) {
        chaptersJob?.cancel()
        chaptersJob = viewModelScope.launch {
            episode.chaptersUrl?.let { url ->
                chapterRepo.fetchAndCacheChapters(episode.audioUrl, url)
            }
            chapterRepo.chaptersForEpisode(episode.audioUrl).collect { list ->
                _chapters.value = list
            }
        }
        val item = androidx.media3.common.MediaItem.Builder()
            .setMediaId(episode.audioUrl)
            .setUri(episode.audioUrl)
            .build()
        controller?.setMediaItem(item)
        controller?.prepare()
        controller?.play()
    }

    fun loadAndPlay(audioUrl: String) {
        viewModelScope.launch {
            val entity = episodeDao.getByAudioUrl(audioUrl) ?: return@launch
            _podcastImageUrl.value = podcastDao.getByUrl(entity.podcastFeedUrl)?.imageUrl
            playEpisode(entity.toDomain())
        }
    }

    fun updateCurrentChapterIndex() {
        val pos = controller?.currentPosition ?: return
        val dur = controller?.duration?.takeIf { it > 0 } ?: 0L
        _currentPositionMs.value = pos
        _durationMs.value = dur
        val idx = _chapters.value.indexOfLast { it.startTimeMs <= pos }
        if (idx >= 0) _currentChapterIndex.value = idx
    }

    fun nextChapter() {
        controller?.sendCustomCommand(
            SessionCommand(PlaybackService.CMD_NEXT_CHAPTER, android.os.Bundle.EMPTY),
            android.os.Bundle.EMPTY
        )
    }

    fun prevChapter() {
        controller?.sendCustomCommand(
            SessionCommand(PlaybackService.CMD_PREV_CHAPTER, android.os.Bundle.EMPTY),
            android.os.Bundle.EMPTY
        )
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes == 0) {
            _sleepTimerSeconds.value = null
            return
        }
        var remaining = minutes * 60
        _sleepTimerSeconds.value = remaining
        sleepTimerJob = viewModelScope.launch {
            while (remaining > 0) {
                delay(1_000L)
                remaining--
                _sleepTimerSeconds.value = remaining
            }
            controller?.pause()
            _sleepTimerSeconds.value = null
        }
    }

    fun setSpeed(speed: Float) {
        val rounded = (speed * 10).toInt() / 10f
        speedPrefs.speed = rounded
        _playbackSpeed.value = rounded
        controller?.setPlaybackParameters(PlaybackParameters(rounded))
    }

    fun seekForward30s() {
        controller?.let {
            val duration = it.duration.takeIf { d -> d > 0 } ?: it.currentPosition
            it.seekTo((it.currentPosition + 30_000L).coerceAtMost(duration))
        }
    }

    fun seekBack30s() {
        controller?.let { it.seekTo((it.currentPosition - 30_000L).coerceAtLeast(0L)) }
    }

    override fun onCleared() {
        controller?.release()
        super.onCleared()
    }
}
