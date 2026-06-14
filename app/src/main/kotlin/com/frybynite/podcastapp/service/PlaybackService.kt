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
import android.util.Log
import androidx.media3.cast.CastPlayer
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "PlaybackSvc"

@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    companion object {
        const val CMD_NEXT_CHAPTER = "com.frybynite.podcastapp.NEXT_CHAPTER"
        const val CMD_PREV_CHAPTER = "com.frybynite.podcastapp.PREV_CHAPTER"
        const val BROWSE_ROOT = "root"
        const val RECENT_ROOT = "root_recent"
        const val PODCAST_PREFIX = "podcast:"
        const val EPISODE_PREFIX = "episode:"
        const val CONTENT_STYLE_LIST_ITEM_HINT_VALUE = 1
    }

    @Inject lateinit var chapterRepo: ChapterRepository
    @Inject lateinit var podcastRepo: PodcastRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var chaptersJob: Job? = null

    private lateinit var player: ExoPlayer
    private lateinit var castPlayer: CastPlayer
    private var activePlayer: Player? = null
    @Volatile private var switchingPlayers = false
    private lateinit var mediaLibrarySession: MediaLibrarySession

    private var chapters: List<Chapter> = emptyList()

    private val castSessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            switchToPlayer(castPlayer)
        }
        override fun onSessionEnded(session: CastSession, error: Int) {
            switchToPlayer(player)
        }
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) {}
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
    }

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
            val pos = (activePlayer ?: player).currentPosition
            when (customCommand.customAction) {
                CMD_NEXT_CHAPTER -> {
                    val target = ChapterNavigator.nextChapterStart(chapters, pos)
                    Log.i(TAG, "CMD_NEXT_CHAPTER: pos=${pos}ms chapters=${chapters.size} target=${target}ms")
                    target?.let { (activePlayer ?: player).seekTo(it) }
                }
                CMD_PREV_CHAPTER -> {
                    val target = ChapterNavigator.prevChapterStart(chapters, pos)
                    Log.i(TAG, "CMD_PREV_CHAPTER: pos=${pos}ms chapters=${chapters.size} target=${target}ms")
                    target?.let { (activePlayer ?: player).seekTo(it) }
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootId = if (params?.isRecent == true) RECENT_ROOT else BROWSE_ROOT
            return Futures.immediateFuture(
                LibraryResult.ofItem(
                    MediaItem.Builder()
                        .setMediaId(rootId)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setIsBrowsable(true)
                                .setIsPlayable(false)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                .setTitle(if (rootId == RECENT_ROOT) "Recent" else "Podcasts")
                                .build()
                        ).build(), params
                )
            )
        }

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
                    parentId == RECENT_ROOT -> {
                        val podcasts = podcastRepo.podcasts.first()
                        val recent = podcasts.flatMap { podcast ->
                            val episodes = podcastRepo.episodesForPodcast(podcast.feedUrl).first()
                            episodes.map { Pair(podcast, it) }
                        }
                            .sortedByDescending { (_, episode) -> episode.pubDate }
                            .take(5)
                            .map { (podcast, episode) -> episode.toMediaItem(podcast.imageUrl) }
                        LibraryResult.ofItemList(recent, params)
                    }
                    parentId == BROWSE_ROOT -> {
                        val podcasts = podcastRepo.podcasts.first()
                        LibraryResult.ofItemList(podcasts.map { it.toMediaItem() }, params)
                    }
                    parentId.startsWith(PODCAST_PREFIX) -> {
                        val feedUrl = parentId.removePrefix(PODCAST_PREFIX)
                        val podcast = podcastRepo.podcasts.first().find { it.feedUrl == feedUrl }
                        val episodes = podcastRepo.episodesForPodcast(feedUrl).first()
                        LibraryResult.ofItemList(
                            episodes.map { it.toMediaItem(podcast?.imageUrl) },
                            params
                        )
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
                if (item.localConfiguration?.uri != null) item
                else item.buildUpon().setUri(item.mediaId).build()
            })
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: starting PlaybackService")
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
                Log.i(TAG, "onMediaItemTransition: audioUrl=$audioUrl title=${mediaItem?.mediaMetadata?.title} reason=$reason")
                chaptersJob?.cancel()
                chaptersJob = serviceScope.launch {
                    chapterRepo.chaptersForEpisode(audioUrl).collect { list ->
                        Log.d(TAG, "chapters updated: count=${list.size} for $audioUrl")
                        chapters = list
                    }
                }
                // If currently casting, update castPlayer with the new media item
                if (!switchingPlayers && ::castPlayer.isInitialized && activePlayer === castPlayer && castPlayer.isCastSessionAvailable) {
                    val newItem = mediaItem.buildUpon().setUri(audioUrl).build()
                    castPlayer.setMediaItem(newItem)
                    castPlayer.prepare()
                }
            }
        })
        castPlayer = CastPlayer(CastContext.getSharedInstance(this))
        activePlayer = player
        CastContext.getSharedInstance(this).sessionManager
            .addSessionManagerListener(castSessionListener, CastSession::class.java)
        val sessionActivity = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaLibrarySession = MediaLibrarySession.Builder(this, activePlayer!!, callback)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaLibrarySession

    override fun onDestroy() {
        Log.i(TAG, "onDestroy: releasing session and player")
        serviceScope.cancel()
        runCatching {
            CastContext.getSharedInstance(this).sessionManager
                .removeSessionManagerListener(castSessionListener, CastSession::class.java)
        }
        if (::castPlayer.isInitialized) castPlayer.release()
        mediaLibrarySession.release()
        player.release()
        super.onDestroy()
    }

    private fun switchToPlayer(newPlayer: Player) {
        switchingPlayers = true
        val currentPosition = activePlayer?.currentPosition ?: 0L
        val currentItem = activePlayer?.currentMediaItem
        val wasPlaying = activePlayer?.isPlaying ?: false
        activePlayer?.stop()

        activePlayer = newPlayer
        val sessionActivity = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val oldSession = mediaLibrarySession
        oldSession.release()
        mediaLibrarySession = MediaLibrarySession.Builder(this, newPlayer, callback)
            .setSessionActivity(sessionActivity)
            .build()

        if (currentItem != null) {
            val item = if (newPlayer === castPlayer && currentItem.localConfiguration?.mimeType == null) {
                currentItem.buildUpon()
                    .setMimeType("audio/mpeg")
                    .build()
            } else {
                currentItem
            }
            newPlayer.setMediaItem(item, currentPosition)
            newPlayer.prepare()
            if (wasPlaying) newPlayer.play()
        }
        switchingPlayers = false
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
            .setArtworkUri(imageUrl?.let { android.net.Uri.parse(it) })
            .setExtras(Bundle().apply {
                putInt(
                    "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT",
                    PlaybackService.CONTENT_STYLE_LIST_ITEM_HINT_VALUE
                )
            })
            .build()
    ).build()

private fun Episode.toMediaItem(podcastImageUrl: String?) = MediaItem.Builder()
    .setMediaId(audioUrl)
    .setUri(audioUrl)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .setArtworkUri(podcastImageUrl?.let { android.net.Uri.parse(it) })
            .build()
    ).build()
