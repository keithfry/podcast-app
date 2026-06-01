package com.podcastapp.ui.player

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.podcastapp.data.repository.ChapterRepository
import com.podcastapp.domain.model.Chapter
import com.podcastapp.domain.model.Episode
import com.podcastapp.service.PlaybackService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chapterRepo: ChapterRepository
) : ViewModel() {

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()

    private val _currentChapterIndex = MutableStateFlow(0)
    val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()

    var controller: MediaController? = null
        private set

    fun connect() {
        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching { controller = future.get() }
        }, MoreExecutors.directExecutor())
    }

    fun playEpisode(episode: Episode) {
        viewModelScope.launch {
            episode.chaptersUrl?.let { url ->
                chapterRepo.fetchAndCacheChapters(episode.audioUrl, url)
            }
            chapterRepo.chaptersForEpisode(episode.audioUrl).collect { list ->
                _chapters.value = list
                updateCurrentChapterIndex()
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

    fun updateCurrentChapterIndex() {
        val pos = controller?.currentPosition ?: return
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

    fun seekForward30s() {
        controller?.let { it.seekTo((it.currentPosition + 30_000L).coerceAtMost(it.duration)) }
    }

    fun seekBack30s() {
        controller?.let { it.seekTo((it.currentPosition - 30_000L).coerceAtLeast(0L)) }
    }

    override fun onCleared() {
        controller?.release()
        super.onCleared()
    }
}
