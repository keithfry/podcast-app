package com.frybynite.podcastapp.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.frybynite.podcastapp.MainActivity
import com.frybynite.podcastapp.data.repository.ChapterRepository
import com.frybynite.podcastapp.data.repository.PodcastRepository
import com.frybynite.podcastapp.domain.model.Chapter
import com.frybynite.podcastapp.domain.model.Episode
import com.frybynite.podcastapp.domain.model.Podcast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    companion object {
        const val CMD_NEXT_CHAPTER = "com.frybynite.podcastapp.NEXT_CHAPTER"
        const val CMD_PREV_CHAPTER = "com.frybynite.podcastapp.PREV_CHAPTER"
        const val BROWSE_ROOT = "root"
        const val PODCAST_PREFIX = "podcast:"
        const val EPISODE_PREFIX = "episode:"
    }

    @Inject lateinit var chapterRepo: ChapterRepository
    @Inject lateinit var podcastRepo: PodcastRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var chaptersJob: Job? = null

    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession

    private var chapters: List<Chapter> = emptyList()

    private val callback = object : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SessionCommand(CMD_NEXT_CHAPTER, Bundle.EMPTY))
                .add(SessionCommand(CMD_PREV_CHAPTER, Bundle.EMPTY))
                .build()
            val commandButtons = listOf(
                CommandButton.Builder()
                    .setDisplayName("Prev Chapter")
                    .setIconResId(android.R.drawable.ic_media_previous)
                    .setSessionCommand(SessionCommand(CMD_PREV_CHAPTER, Bundle.EMPTY))
                    .build(),
                CommandButton.Builder()
                    .setDisplayName("Next Chapter")
                    .setIconResId(android.R.drawable.ic_media_next)
                    .setSessionCommand(SessionCommand(CMD_NEXT_CHAPTER, Bundle.EMPTY))
                    .build()
            )
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setCustomLayout(commandButtons)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_NEXT_CHAPTER ->
                    ChapterNavigator.nextChapterStart(chapters, player.currentPosition)
                        ?.let { player.seekTo(it) }
                CMD_PREV_CHAPTER ->
                    ChapterNavigator.prevChapterStart(chapters, player.currentPosition)
                        ?.let { player.seekTo(it) }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(
                LibraryResult.ofItem(
                    MediaItem.Builder()
                        .setMediaId(BROWSE_ROOT)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setIsBrowsable(true)
                                .setIsPlayable(false)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                .setTitle("Podcasts")
                                .build()
                        ).build(), params
                )
            )

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            serviceScope.future {
                when {
                    parentId == BROWSE_ROOT -> {
                        val podcasts = podcastRepo.podcasts.first()
                        LibraryResult.ofItemList(podcasts.map { it.toMediaItem() }, params)
                    }
                    parentId.startsWith(PODCAST_PREFIX) -> {
                        val feedUrl = parentId.removePrefix(PODCAST_PREFIX)
                        val episodes = podcastRepo.episodesForPodcast(feedUrl).first()
                        LibraryResult.ofItemList(episodes.map { it.toMediaItem() }, params)
                    }
                    else -> LibraryResult.ofItemList(emptyList(), params)
                }
            }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { item ->
                item.buildUpon().setUri(item.mediaId).build()
            })
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus= */ true
            )
            .build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val audioUrl = mediaItem?.mediaId ?: return
                chaptersJob?.cancel()
                chaptersJob = serviceScope.launch {
                    chapterRepo.chaptersForEpisode(audioUrl).collect { list ->
                        chapters = list
                    }
                }
            }
        })
        val sessionActivity = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaLibrarySession = MediaLibrarySession.Builder(this, player, callback)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaLibrarySession

    override fun onDestroy() {
        serviceScope.cancel()
        mediaLibrarySession.release()
        player.release()
        super.onDestroy()
    }
}

private fun Podcast.toMediaItem() = MediaItem.Builder()
    .setMediaId("${PlaybackService.PODCAST_PREFIX}$feedUrl")
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(author)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST)
            .build()
    ).build()

private fun Episode.toMediaItem() = MediaItem.Builder()
    .setMediaId(audioUrl)
    .setUri(audioUrl)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .build()
    ).build()
