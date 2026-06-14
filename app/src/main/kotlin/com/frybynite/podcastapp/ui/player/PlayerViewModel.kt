package com.frybynite.podcastapp.ui.player

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.media3.common.PlaybackParameters
import com.frybynite.podcastapp.data.db.dao.EpisodeDao
import com.frybynite.podcastapp.data.db.dao.PodcastDao
import com.frybynite.podcastapp.data.preferences.SpeedPreferences
import com.frybynite.podcastapp.data.repository.ChapterRepository
import com.frybynite.podcastapp.data.repository.toDomain
import com.frybynite.podcastapp.deepdive.DeepDiveOrchestrator
import com.frybynite.podcastapp.deepdive.ModelDownloadManager
import com.frybynite.podcastapp.deepdive.ModelDownloadState
import com.frybynite.podcastapp.deepdive.TextSummarizer
import com.frybynite.podcastapp.domain.model.Chapter
import com.frybynite.podcastapp.domain.model.Episode
import com.frybynite.podcastapp.service.PlaybackService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.frybynite.podcastapp.deepdive.DeepDiveRouter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.util.Log
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import javax.inject.Inject

private const val TAG = "PlayerVM"

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chapterRepo: ChapterRepository,
    private val episodeDao: EpisodeDao,
    private val podcastDao: PodcastDao,
    private val podcastRepo: com.frybynite.podcastapp.data.repository.PodcastRepository,
    private val deepDiveDao: com.frybynite.podcastapp.data.db.dao.DeepDiveDao,
    private val speedPrefs: SpeedPreferences,
    private val deepDiveOrchestrator: DeepDiveOrchestrator,
    private val summarizer: TextSummarizer,
    private val modelDownloadManager: ModelDownloadManager
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

    private val _artworkUrl = MutableStateFlow<String?>(null)
    val artworkUrl: StateFlow<String?> = _artworkUrl.asStateFlow()

    private val _podcastTitle = MutableStateFlow<String?>(null)
    val podcastTitle: StateFlow<String?> = _podcastTitle.asStateFlow()

    private val _episodeTitle = MutableStateFlow<String?>(null)
    val episodeTitle: StateFlow<String?> = _episodeTitle.asStateFlow()

    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    // Sleep timer: remaining seconds, null = not active
    private val _sleepTimerSeconds = MutableStateFlow<Int?>(null)
    val sleepTimerSeconds: StateFlow<Int?> = _sleepTimerSeconds.asStateFlow()
    private var sleepTimerJob: Job? = null

    private var chaptersJob: Job? = null
    private var positionSaveJob: Job? = null
    private var currentEpisode: Episode? = null
    private var episodeLoaded = false

    private val _deepDiveState = MutableStateFlow<DeepDiveState>(DeepDiveState.Idle)
    val deepDiveState: StateFlow<DeepDiveState> = _deepDiveState.asStateFlow()
    val modelDownloadState = modelDownloadManager.state

    // Chapter the deep dive was launched from; the "More About This…" row is inserted after it.
    private val _deepDiveChapterIndex = MutableStateFlow<Int?>(null)
    val deepDiveChapterIndex: StateFlow<Int?> = _deepDiveChapterIndex.asStateFlow()

    // Set of chapter URLs that have a cached deep dive file for the current episode.
    private val _cachedDeepDiveUrls = MutableStateFlow<Set<String>>(emptySet())
    val cachedDeepDiveUrls: StateFlow<Set<String>> = _cachedDeepDiveUrls.asStateFlow()
    private var cachedDeepDiveJob: kotlinx.coroutines.Job? = null

    private var pendingTtsFile: java.io.File? = null
    private var pendingDeepDiveUrl: String? = null

    private val toneGen by lazy { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }
    private var tickingJob: Job? = null
    private var exitToneJob: Job? = null
    private var deepDiveResumePositionMs: Long = 0L
    private var deepDiveResumeEpisodeUri: String? = null

    var controller: MediaController? = null
        private set

    private val castListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) { _isCasting.value = true }
        override fun onSessionEnded(session: CastSession, error: Int) { _isCasting.value = false }
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) { _isCasting.value = true }
        override fun onSessionSuspended(session: CastSession, reason: Int) { _isCasting.value = false }
        override fun onSessionStartFailed(session: CastSession, error: Int) {}
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
    }

    init {
        viewModelScope.launch {
            DeepDiveRouter.pendingUrl.collectLatest { url ->
                if (url.isNotEmpty()) moreAboutThis(url)
            }
        }
        viewModelScope.launch {
            modelDownloadManager.state.collectLatest { state ->
                if (state is ModelDownloadState.Complete) {
                    val url = pendingDeepDiveUrl
                    if (url != null) {
                        android.util.Log.i("DeepDive", "Model download complete — auto-starting deep dive for $url")
                        pendingDeepDiveUrl = null
                        moreAboutThis(url)
                    }
                }
            }
        }
        runCatching {
            val castContext = CastContext.getSharedInstance(context)
            _isCasting.value = castContext.sessionManager.currentCastSession != null
            castContext.sessionManager.addSessionManagerListener(castListener, CastSession::class.java)
        }
    }

    fun connect(audioUrl: String) {
        Log.i(TAG, "connect: audioUrl=$audioUrl")
        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching {
                controller = future.get()
                Log.i(TAG, "connect: MediaController connected, setting speed=${speedPrefs.speed}")
                controller?.setPlaybackParameters(PlaybackParameters(speedPrefs.speed))
                controller?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        Log.d(TAG, "onIsPlayingChanged: playing=$playing episodeLoaded=$episodeLoaded")
                        // Only reflect play state and save position once this episode is loaded
                        if (!episodeLoaded) return
                        _isPlaying.value = playing
                        if (playing) {
                            positionSaveJob?.cancel()
                            positionSaveJob = viewModelScope.launch {
                                while (true) {
                                    delay(10_000L)
                                    savePosition()
                                }
                            }
                        } else {
                            positionSaveJob?.cancel()
                            positionSaveJob = null
                            viewModelScope.launch { savePosition() }
                        }
                    }
                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int
                    ) {
                        Log.d(TAG, "onPositionDiscontinuity: ${oldPosition.positionMs}ms -> ${newPosition.positionMs}ms reason=$reason")
                        updateCurrentChapterIndex()
                        val ep = currentEpisode ?: return
                        val pos = newPosition.positionMs.takeIf { it > 0L } ?: return
                        // Guard: only save if controller is playing this episode, not a previous one
                        if (controller?.currentMediaItem?.mediaId != ep.audioUrl) return
                        viewModelScope.launch { episodeDao.updateLastPosition(ep.audioUrl, pos) }
                    }
                    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                        val incomingId = mediaItem?.mediaId ?: return
                        Log.i(TAG, "onMediaItemTransition: mediaId=$incomingId reason=$reason deepDiveState=${_deepDiveState.value}")
                        if (!incomingId.startsWith("tts://") && _deepDiveState.value == DeepDiveState.Playing) {
                            Log.i(TAG, "onMediaItemTransition: deep dive ended by media transition, playing exit tone")
                            controller?.pause()
                            exitToneJob = viewModelScope.launch {
                                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
                                delay(400)
                                pendingTtsFile = null
                                _deepDiveState.value = DeepDiveState.Idle
                                _deepDiveChapterIndex.value = null
                                controller?.seekTo(deepDiveResumePositionMs)
                                controller?.play()
                                exitToneJob = null
                            }
                        }
                        updateCurrentChapterIndex()
                    }
                })
                loadMetadata(audioUrl)
            }.onFailure { e ->
                Log.e(TAG, "connect: MediaController failed", e)
            }
        }, context.mainExecutor)
    }

    fun playEpisode(episode: Episode) {
        Log.i(TAG, "playEpisode: title=${episode.title} audioUrl=${episode.audioUrl} chaptersUrl=${episode.chaptersUrl}")
        // Chapters already loaded by loadMetadata; only re-fetch if switching to a different episode
        if (episode.audioUrl != currentEpisode?.audioUrl || _chapters.value.isEmpty()) {
            chaptersJob?.cancel()
            chaptersJob = viewModelScope.launch {
                episode.chaptersUrl?.let { url ->
                    Log.d(TAG, "playEpisode: fetching chapters from $url")
                    chapterRepo.fetchAndCacheChapters(episode.audioUrl, url)
                }
                chapterRepo.chaptersForEpisode(episode.audioUrl).collect { list ->
                    Log.d(TAG, "playEpisode: chapters updated count=${list.size}")
                    _chapters.value = list
                }
            }
        }
        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(_podcastTitle.value)
            .apply { _artworkUrl.value?.let { setArtworkUri(android.net.Uri.parse(it)) } }
            .build()
        val playUri = episode.downloadPath
            ?.let { android.net.Uri.fromFile(java.io.File(it)) }
            ?: android.net.Uri.parse(episode.audioUrl)
        Log.d(TAG, "playEpisode: uri=$playUri (local=${episode.downloadPath != null})")
        val item = androidx.media3.common.MediaItem.Builder()
            .setMediaId(episode.audioUrl)
            .setUri(playUri)
            .setMediaMetadata(metadata)
            .build()
        controller?.setMediaItem(item, episode.lastPositionMs)
        controller?.prepare()
        controller?.play()
        episodeLoaded = true
    }

    fun loadMetadata(audioUrl: String) {
        Log.i(TAG, "loadMetadata: audioUrl=$audioUrl")
        // Synchronous reset — clears stale state from any previously loaded episode
        chaptersJob?.cancel()
        positionSaveJob?.cancel()
        positionSaveJob = null
        currentEpisode = null
        episodeLoaded = false
        _chapters.value = emptyList()
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
        _currentChapterIndex.value = 0
        _isPlaying.value = false
        viewModelScope.launch {
            val entity = episodeDao.getByAudioUrl(audioUrl)
            if (entity == null) {
                Log.w(TAG, "loadMetadata: no episode found for audioUrl=$audioUrl")
                return@launch
            }
            val podcast = podcastDao.getByUrl(entity.podcastFeedUrl)
            Log.d(TAG, "loadMetadata: podcast=${podcast?.title} imageUrl=${podcast?.imageUrl} episodeImageUrl=${entity.imageUrl}")
            _artworkUrl.value = entity.imageUrl ?: podcast?.imageUrl
            _podcastTitle.value = podcast?.title
            _episodeTitle.value = entity.title
            if (entity.downloadStatus == "NONE" && entity.downloadPath == null) {
                Log.i(TAG, "loadMetadata: queuing background download for ${entity.audioUrl}")
                podcastRepo.downloadEpisode(entity.audioUrl)
            }
            cachedDeepDiveJob?.cancel()
            cachedDeepDiveJob = viewModelScope.launch {
                deepDiveDao.flowForEpisode(entity.audioUrl).collect { rows ->
                    _cachedDeepDiveUrls.value = rows.filter { java.io.File(it.filePath).exists() }
                        .map { it.chapterUrl }.toSet()
                }
            }
            val episode = entity.toDomain()
            currentEpisode = episode
            episodeLoaded = false
            // Seed position and duration from stored data so UI shows correct state before play
            _currentPositionMs.value = episode.lastPositionMs
            _durationMs.value = entity.durationSeconds * 1000L
            // Fetch chapters so chapter list is visible before playback starts
            chaptersJob?.cancel()
            chaptersJob = viewModelScope.launch {
                entity.chaptersUrl?.let { url ->
                    Log.d(TAG, "loadMetadata: fetching chapters from $url")
                    chapterRepo.fetchAndCacheChapters(entity.audioUrl, url)
                }
                chapterRepo.chaptersForEpisode(entity.audioUrl).collect { list ->
                    Log.d(TAG, "loadMetadata: chapters updated count=${list.size}")
                    _chapters.value = list
                    val pos = currentEpisode?.lastPositionMs ?: 0L
                    if (list.isNotEmpty()) {
                        val idx = list.indexOfLast { it.startTimeMs <= pos }
                        if (idx >= 0) _currentChapterIndex.value = idx
                    }
                }
            }
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else if (episodeLoaded) {
            c.play()
        } else {
            val ep = currentEpisode ?: return
            playEpisode(ep)
        }
    }

    private suspend fun savePosition() {
        val ep = currentEpisode ?: return
        if (controller?.currentMediaItem?.mediaId != ep.audioUrl) return
        val pos = controller?.currentPosition?.takeIf { it > 0L } ?: return
        Log.d(TAG, "savePosition: audioUrl=${ep.audioUrl} pos=${pos}ms")
        episodeDao.updateLastPosition(ep.audioUrl, pos)
    }

    fun updateCurrentChapterIndex() {
        val s = _deepDiveState.value
        // Loading: episode paused — freeze everything so bar stays at the saved position.
        if (s is DeepDiveState.Loading) return
        val pos = controller?.currentPosition ?: return
        val dur = controller?.duration?.takeIf { it > 0 } ?: 0L
        // Don't overwrite a known position with 0 during prepare/seek transitions
        if (pos > 0L || _currentPositionMs.value == 0L) _currentPositionMs.value = pos
        if (dur > 0L) _durationMs.value = dur
        // Playing: update bar for TTS progress but don't move the chapter highlight.
        if (s == DeepDiveState.Playing) return
        val idx = _chapters.value.indexOfLast { it.startTimeMs <= pos }
        if (idx >= 0 && idx != _currentChapterIndex.value) {
            Log.d(TAG, "updateCurrentChapterIndex: chapter changed ${_currentChapterIndex.value} -> $idx at pos=${pos}ms")
            _currentChapterIndex.value = idx
        } else if (idx >= 0) {
            _currentChapterIndex.value = idx
        }
    }

    fun nextChapter() {
        Log.i(TAG, "nextChapter: pos=${controller?.currentPosition}ms currentIdx=${_currentChapterIndex.value}")
        controller?.sendCustomCommand(
            SessionCommand(PlaybackService.CMD_NEXT_CHAPTER, android.os.Bundle.EMPTY),
            android.os.Bundle.EMPTY
        )
    }

    fun prevChapter() {
        Log.i(TAG, "prevChapter: pos=${controller?.currentPosition}ms currentIdx=${_currentChapterIndex.value}")
        controller?.sendCustomCommand(
            SessionCommand(PlaybackService.CMD_PREV_CHAPTER, android.os.Bundle.EMPTY),
            android.os.Bundle.EMPTY
        )
    }

    fun setSleepTimer(minutes: Int) {
        if (minutes == 0) {
            Log.i(TAG, "setSleepTimer: cancelled")
            sleepTimerJob?.cancel()
            _sleepTimerSeconds.value = null
            return
        }
        Log.i(TAG, "setSleepTimer: ${minutes}min")
        sleepTimerJob?.cancel()
        var remaining = minutes * 60
        _sleepTimerSeconds.value = remaining
        sleepTimerJob = viewModelScope.launch {
            while (remaining > 0) {
                delay(1_000L)
                remaining--
                _sleepTimerSeconds.value = remaining
            }
            Log.i(TAG, "setSleepTimer: fired, pausing playback")
            controller?.pause()
            _sleepTimerSeconds.value = null
        }
    }

    fun setSpeed(speed: Float) {
        val rounded = (speed * 10).toInt() / 10f
        Log.i(TAG, "setSpeed: ${_playbackSpeed.value} -> $rounded")
        speedPrefs.speed = rounded
        _playbackSpeed.value = rounded
        controller?.setPlaybackParameters(PlaybackParameters(rounded))
    }

    fun seekForward30s() {
        controller?.let {
            val pos = it.currentPosition
            val duration = it.duration.takeIf { d -> d > 0 } ?: pos
            val target = (pos + 30_000L).coerceAtMost(duration)
            Log.i(TAG, "seekForward30s: ${pos}ms -> ${target}ms (duration=${duration}ms)")
            it.seekTo(target)
        }
    }

    fun seekBack10s() {
        controller?.let {
            val pos = it.currentPosition
            val target = (pos - 10_000L).coerceAtLeast(0L)
            Log.i(TAG, "seekBack10s: ${pos}ms -> ${target}ms")
            it.seekTo(target)
        }
    }

    fun seekBack30s() {
        controller?.let {
            val pos = it.currentPosition
            val target = (pos - 30_000L).coerceAtLeast(0L)
            Log.i(TAG, "seekBack30s: ${pos}ms -> ${target}ms")
            it.seekTo(target)
        }
    }

    fun moreAboutThis(url: String? = null, sourceChapterIndex: Int? = null) {
        val resolvedUrl = url
            ?: _chapters.value.getOrNull(_currentChapterIndex.value)?.url
            ?: run {
                android.util.Log.w("DeepDive", "moreAboutThis: no URL for current chapter")
                _deepDiveState.value = DeepDiveState.Error("No link for this segment")
                return
            }
        android.util.Log.i("DeepDive", "moreAboutThis: url=$resolvedUrl")
        pendingDeepDiveUrl = resolvedUrl
        if (!summarizer.isModelAvailable()) {
            android.util.Log.i("DeepDive", "moreAboutThis: model not available — requesting download")
            _deepDiveState.value = DeepDiveState.ModelRequired
            return
        }
        val currentMediaId = controller?.currentMediaItem?.mediaId ?: return
        // Only capture a new resume position when playing the actual episode (not TTS, not during
        // the exit-tone window where deepDiveState is still Playing but item is the resume item).
        val inDeepDive = currentMediaId.startsWith("tts://") || _deepDiveState.value == DeepDiveState.Playing
        if (!inDeepDive) {
            deepDiveResumePositionMs = controller?.currentPosition ?: return
            deepDiveResumeEpisodeUri = currentMediaId
        }
        if (deepDiveResumeEpisodeUri == null) return
        _deepDiveChapterIndex.value = sourceChapterIndex ?: _currentChapterIndex.value
        android.util.Log.i("DeepDive", "moreAboutThis: savedPos=${deepDiveResumePositionMs}ms episodeUri=$deepDiveResumeEpisodeUri sourceChapter=${_deepDiveChapterIndex.value}")

        exitToneJob?.cancel()
        exitToneJob = null

        // Capture as local vals so coroutine reads a snapshot, not a mutable var.
        val episodeUri = deepDiveResumeEpisodeUri
        val resumePos = deepDiveResumePositionMs

        viewModelScope.launch {
            runCatching {
                val chapterTitle = _chapters.value.getOrNull(_deepDiveChapterIndex.value ?: -1)?.title
                val cached = deepDiveOrchestrator.isCached(resolvedUrl, episodeUri)

                if (!cached) {
                    // Show loading overlay + tick + pause only when generation is needed.
                    _deepDiveState.value = DeepDiveState.Loading(DeepDiveStep.FETCHING)
                    controller?.pause()
                    tickingJob?.cancel()
                    tickingJob = viewModelScope.launch {
                        while (true) {
                            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 35)
                            delay(1200)
                        }
                    }
                }

                val ttsFile = deepDiveOrchestrator.process(resolvedUrl, episodeUri, chapterTitle) { step ->
                    _deepDiveState.value = DeepDiveState.Loading(step)
                    android.util.Log.i("DeepDive", "moreAboutThis: step=$step")
                }
                pendingTtsFile = ttsFile

                tickingJob?.cancel()
                tickingJob = null
                // On cache hit path the episode is still playing; pause now before injecting TTS.
                if (cached) controller?.pause()
                toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 400)
                delay(500)

                val ttsItem = androidx.media3.common.MediaItem.Builder()
                    .setMediaId("tts://${ttsFile.name}")
                    .setUri(android.net.Uri.fromFile(ttsFile))
                    .build()
                val resumeItem = androidx.media3.common.MediaItem.Builder()
                    .setMediaId(episodeUri!!)
                    .setUri(episodeUri!!)
                    .build()

                android.util.Log.i("DeepDive", "moreAboutThis: injecting TTS item, resuming at ${resumePos}ms")
                controller?.setMediaItems(listOf(ttsItem, resumeItem))
                controller?.prepare()
                controller?.play()
                _deepDiveState.value = DeepDiveState.Playing
                android.util.Log.i("DeepDive", "moreAboutThis: playing")
            }.onFailure { e ->
                tickingJob?.cancel()
                tickingJob = null
                android.util.Log.e("DeepDive", "moreAboutThis failed", e)
                _deepDiveState.value = DeepDiveState.Error(e.message ?: "Deep dive failed")
                _deepDiveChapterIndex.value = null
                controller?.play()
            }
        }
    }

    fun downloadModel() {
        viewModelScope.launch {
            modelDownloadManager.downloadModel(pendingDeepDiveUrl ?: "")
        }
    }

    fun skipDeepDive() {
        if (_deepDiveState.value != DeepDiveState.Playing) return
        val episodeUri = deepDiveResumeEpisodeUri ?: return
        Log.i(TAG, "skipDeepDive: resuming episode at ${deepDiveResumePositionMs}ms uri=$episodeUri")
        exitToneJob?.cancel()
        controller?.pause()
        exitToneJob = viewModelScope.launch {
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
            delay(400)
            val resumeItem = androidx.media3.common.MediaItem.Builder()
                .setMediaId(episodeUri)
                .setUri(episodeUri)
                .build()
            controller?.setMediaItem(resumeItem)
            controller?.prepare()
            controller?.seekTo(deepDiveResumePositionMs)
            controller?.play()
            pendingTtsFile = null
            _deepDiveState.value = DeepDiveState.Idle
            _deepDiveChapterIndex.value = null
            exitToneJob = null
        }
    }

    fun jumpToChapter(startTimeMs: Long) {
        if (_deepDiveState.value != DeepDiveState.Playing) {
            Log.i(TAG, "jumpToChapter: seeking to ${startTimeMs}ms")
            controller?.seekTo(startTimeMs)
            return
        }
        Log.i(TAG, "jumpToChapter: interrupting deep dive, jumping to ${startTimeMs}ms")
        exitToneJob?.cancel()
        exitToneJob = null
        val episodeUri = deepDiveResumeEpisodeUri ?: return
        val resumeItem = androidx.media3.common.MediaItem.Builder()
            .setMediaId(episodeUri)
            .setUri(episodeUri)
            .build()
        controller?.setMediaItem(resumeItem)
        controller?.prepare()
        controller?.seekTo(startTimeMs)
        controller?.play()
        pendingTtsFile = null
        _deepDiveState.value = DeepDiveState.Idle
        _deepDiveChapterIndex.value = null
    }

    fun dismissDeepDiveError() {
        _deepDiveState.value = DeepDiveState.Idle
        _deepDiveChapterIndex.value = null
    }

    override fun onCleared() {
        Log.i(TAG, "onCleared: releasing controller and resources")
        tickingJob?.cancel()
        exitToneJob?.cancel()
        positionSaveJob?.cancel()
        val ep = currentEpisode
        val pos = controller?.currentPosition?.takeIf { it > 0L }
        toneGen.release()
        controller?.release()
        if (ep != null && pos != null) {
            Log.d(TAG, "onCleared: saving position ${pos}ms for ${ep.audioUrl}")
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                episodeDao.updateLastPosition(ep.audioUrl, pos)
            }
        }
        runCatching {
            CastContext.getSharedInstance(context).sessionManager
                .removeSessionManagerListener(castListener, CastSession::class.java)
        }
        super.onCleared()
    }
}

enum class DeepDiveStep { FETCHING, SUMMARIZING, SYNTHESIZING }

sealed class DeepDiveState {
    data object Idle : DeepDiveState()
    data object ModelRequired : DeepDiveState()
    data class Loading(val step: DeepDiveStep = DeepDiveStep.FETCHING) : DeepDiveState()
    data object Playing : DeepDiveState()
    data class Error(val message: String) : DeepDiveState()
}
