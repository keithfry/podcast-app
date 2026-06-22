# Global PlaybackController Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace per-ViewModel `MediaController` ownership with a single `@Singleton PlaybackController` that owns the one `MediaController` for the app, exposes playback state as `StateFlow`s to all screens, and pre-fetches chapters when the playing episode changes.

**Architecture:** A new `PlaybackController` singleton connects to `PlaybackService` once at app start via `MediaController.Builder.buildAsync()`. It exposes `currentlyPlayingUrl`, `isPlaying`, and `controller` as `StateFlow`s. `PlayerViewModel` and `EpisodeListViewModel` inject it and drop their own `MediaController` lifecycle code. `EpisodeListViewModel`'s download-then-autoplay logic moves into `PlaybackController`.

**Tech Stack:** Kotlin, Hilt (`@Singleton`), Media3 `MediaController`/`SessionToken`, Room `Flow`, WorkManager, Jetpack Compose

## Global Constraints

- Work in worktree `worktrees/global-playback-controller` on branch `global-playback-controller` (branched from `episode-list-play-icon`)
- Kotlin only, no Java
- No database schema changes except adding one query to `EpisodeDao`
- Do not modify `PlaybackService`, `DownloadWorker`, or the Compose UI layer (EpisodeListScreen, EpisodeRow)
- Follow existing import/formatting conventions

---

### Task 1: Create worktree and add `getByAudioUrlFlow` to EpisodeDao

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podlore/data/db/dao/EpisodeDao.kt`

**Interfaces:**
- Produces: `fun getByAudioUrlFlow(audioUrl: String): Flow<EpisodeEntity?>` — Room `@Query` that emits a single episode row whenever it changes, or `null` if not found. Used by `PlaybackController` to observe download status for pending auto-play.

- [ ] **Step 1: Create the worktree and branch**

```bash
cd /Users/keithfry/projects/podcast-app
git worktree add worktrees/global-playback-controller -b global-playback-controller episode-list-play-icon
cd worktrees/global-playback-controller
```

Expected: new directory `worktrees/global-playback-controller` checked out on branch `global-playback-controller`.

- [ ] **Step 2: Add `getByAudioUrlFlow` to EpisodeDao**

Open `app/src/main/kotlin/com/frybynite/podlore/data/db/dao/EpisodeDao.kt`. After the existing `getByAudioUrl` suspend function (line 13), add:

```kotlin
@Query("SELECT * FROM episodes WHERE audioUrl = :audioUrl")
fun getByAudioUrlFlow(audioUrl: String): Flow<EpisodeEntity?>
```

The full file after the change (only the top of the interface changes — show just the changed section):

```kotlin
@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE podcastFeedUrl = :feedUrl ORDER BY pubDate DESC")
    fun getForPodcast(feedUrl: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE audioUrl = :audioUrl")
    suspend fun getByAudioUrl(audioUrl: String): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE audioUrl = :audioUrl")
    fun getByAudioUrlFlow(audioUrl: String): Flow<EpisodeEntity?>

    // ... rest unchanged
```

- [ ] **Step 3: Build to verify**

```bash
cd worktrees/global-playback-controller
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL, zero errors.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podlore/data/db/dao/EpisodeDao.kt
git commit -m "feat: add getByAudioUrlFlow to EpisodeDao"
```

---

### Task 2: Create PlaybackController

**Files:**
- Create: `app/src/main/kotlin/com/frybynite/podlore/playback/PlaybackController.kt`

**Interfaces:**
- Consumes: `EpisodeDao.getByAudioUrlFlow(audioUrl)` (Task 1), `PodcastRepository.downloadEpisode()`, `PodcastRepository.cancelDownload()`, `ChapterRepository.fetchAndCacheChapters()`
- Produces:
  - `val currentlyPlayingUrl: StateFlow<String?>` — `mediaId` of currently loaded item, null if nothing playing
  - `val isPlaying: StateFlow<Boolean>` — true while ExoPlayer actively plays
  - `val controller: StateFlow<MediaController?>` — null until `buildAsync()` resolves; expose for direct Media3 command use by `PlayerViewModel`
  - `fun play(episode: Episode)` — drives playback or download+autoplay
  - `fun pause()`
  - `fun resume()`

- [ ] **Step 1: Create the file**

Create `app/src/main/kotlin/com/frybynite/podlore/playback/PlaybackController.kt`:

```kotlin
package com.frybynite.podlore.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.frybynite.podlore.data.db.dao.EpisodeDao
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val episodeDao: EpisodeDao,
    private val podcastRepo: PodcastRepository,
    private val chapterRepo: ChapterRepository
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
            _pendingAutoPlayUrl.filterNotNull().collectLatest { pending ->
                episodeDao.getByAudioUrlFlow(pending).collect { entity ->
                    if (entity?.downloadStatus == "DONE" && entity.downloadPath != null) {
                        _pendingAutoPlayUrl.value = null
                        val item = MediaItem.Builder()
                            .setMediaId(entity.audioUrl)
                            .setUri(Uri.fromFile(File(entity.downloadPath)))
                            .build()
                        _controller.value?.setMediaItem(item)
                        _controller.value?.prepare()
                        _controller.value?.play()
                    }
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
                    .build()
                _controller.value?.setMediaItem(item)
                _controller.value?.prepare()
                _controller.value?.play()
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
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL, zero errors.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podlore/playback/PlaybackController.kt
git commit -m "feat: add PlaybackController singleton"
```

---

### Task 3: Migrate PlayerViewModel to use PlaybackController

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podlore/ui/player/PlayerViewModel.kt`

**Interfaces:**
- Consumes: `PlaybackController.controller: StateFlow<MediaController?>`, `PlaybackController.isPlaying: StateFlow<Boolean>` (Task 2)
- Produces: same public API as before (`connect()`, `playEpisode()`, `togglePlayPause()`, etc.) — callers unchanged

- [ ] **Step 1: Add PlaybackController to constructor and replace controller field**

In `PlayerViewModel.kt`:

**Add import:**
```kotlin
import com.frybynite.podlore.playback.PlaybackController
import kotlinx.coroutines.flow.filterNotNull
```

**Add to constructor** (after `transcriptRepo: TranscriptRepository`):
```kotlin
private val playbackController: PlaybackController,
```

**Remove** `var controller: MediaController?` and its `private set` (line ~141). **Replace** with a computed property:
```kotlin
private val ctrl get() = playbackController.controller.value
```

Also add a field for the listener so it can be removed on `onCleared()`:
```kotlin
private var playerListener: Player.Listener? = null
```

- [ ] **Step 2: Replace `connect()` implementation**

Replace the entire `connect()` method body. The new version gets the controller from `PlaybackController` (waiting if not yet ready), then adds PlayerViewModel's own listener for its specific concerns (position saving, deep dive detection):

```kotlin
fun connect(audioUrl: String) {
    Log.i(TAG, "connect: audioUrl=$audioUrl")
    viewModelScope.launch {
        val c = playbackController.controller.filterNotNull().first()
        c.setPlaybackParameters(PlaybackParameters(speedPrefs.speed))
        playerListener?.let { c.removeListener(it) }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                Log.d(TAG, "onIsPlayingChanged: playing=$playing episodeLoaded=$episodeLoaded")
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
                if (ctrl?.currentMediaItem?.mediaId != ep.audioUrl) return
                viewModelScope.launch { episodeDao.updateLastPosition(ep.audioUrl, pos) }
            }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                val incomingId = mediaItem?.mediaId ?: return
                Log.i(TAG, "onMediaItemTransition: mediaId=$incomingId reason=$reason deepDiveState=${_deepDiveState.value}")
                if (!incomingId.startsWith("tts://") && _deepDiveState.value == DeepDiveState.Playing) {
                    Log.i(TAG, "onMediaItemTransition: deep dive ended by media transition, playing exit tone")
                    ctrl?.pause()
                    exitToneJob = viewModelScope.launch {
                        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
                        delay(400)
                        pendingTtsFile = null
                        _deepDiveState.value = DeepDiveState.Idle
                        _deepDiveChapterIndex.value = null
                        ctrl?.seekTo(deepDiveResumePositionMs)
                        ctrl?.play()
                        exitToneJob = null
                    }
                }
                updateCurrentChapterIndex()
            }
        }
        playerListener = listener
        c.addListener(listener)
        loadMetadata(audioUrl)
    }
}
```

- [ ] **Step 3: Replace all remaining `controller?.` with `ctrl?.`**

In the rest of the file (outside of `connect()`), every occurrence of `controller?.` must become `ctrl?.`. Use find-and-replace. The compiler will catch any missed occurrences.

Also update `savePosition()` which references `controller?.currentMediaItem` and `controller?.currentPosition`:
```kotlin
private suspend fun savePosition() {
    val ep = currentEpisode ?: return
    if (ctrl?.currentMediaItem?.mediaId != ep.audioUrl) return
    val pos = ctrl?.currentPosition?.takeIf { it > 0L } ?: return
    Log.d(TAG, "savePosition: audioUrl=${ep.audioUrl} pos=${pos}ms")
    episodeDao.updateLastPosition(ep.audioUrl, pos)
}
```

- [ ] **Step 4: Update `onCleared()` — remove controller release**

In `onCleared()`, remove the `controller?.release()` call. The controller belongs to `PlaybackController` (singleton), never released. Also remove the local controller position-save that referenced `controller`:

Replace:
```kotlin
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
```

With:
```kotlin
override fun onCleared() {
    Log.i(TAG, "onCleared: releasing resources")
    tickingJob?.cancel()
    exitToneJob?.cancel()
    positionSaveJob?.cancel()
    val ep = currentEpisode
    val pos = ctrl?.currentPosition?.takeIf { it > 0L }
    toneGen.release()
    playerListener?.let { ctrl?.removeListener(it) }
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
```

- [ ] **Step 5: Remove now-unused MediaController imports**

Remove these imports if no longer referenced:
```kotlin
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import android.content.ComponentName
```

(Keep them if still referenced elsewhere in the file — the compiler will warn about unused imports.)

- [ ] **Step 6: Build to verify**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL, zero errors. Fix any remaining `controller?.` → `ctrl?.` misses that the compiler flags.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podlore/ui/player/PlayerViewModel.kt
git commit -m "feat: migrate PlayerViewModel to use PlaybackController"
```

---

### Task 4: Migrate EpisodeListViewModel to use PlaybackController

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podlore/ui/episodes/EpisodeListViewModel.kt`

**Interfaces:**
- Consumes: `PlaybackController.currentlyPlayingUrl`, `PlaybackController.isPlaying`, `PlaybackController.play()`, `PlaybackController.pause()`, `PlaybackController.resume()` (Task 2)
- Produces: same public API as the `episode-list-play-icon` branch (`currentlyPlayingUrl`, `isPlaying`, `onPlayPause()`) — `EpisodeListScreen` unchanged

- [ ] **Step 1: Replace the file**

Replace `app/src/main/kotlin/com/frybynite/podlore/ui/episodes/EpisodeListViewModel.kt` with:

```kotlin
package com.frybynite.podlore.ui.episodes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frybynite.podlore.data.db.dao.PodcastDao
import com.frybynite.podlore.data.preferences.EpisodeListPreferences
import com.frybynite.podlore.data.repository.PodcastRepository
import com.frybynite.podlore.domain.model.Episode
import com.frybynite.podlore.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EpisodeListViewModel @Inject constructor(
    private val repo: PodcastRepository,
    private val podcastDao: PodcastDao,
    private val episodeListPrefs: EpisodeListPreferences,
    private val playbackController: PlaybackController,
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

    val currentlyPlayingUrl: StateFlow<String?> = playbackController.currentlyPlayingUrl
    val isPlaying: StateFlow<Boolean> = playbackController.isPlaying

    init {
        viewModelScope.launch {
            _podcastImageUrl.value = podcastDao.getByUrl(feedUrl)?.imageUrl
        }
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
        if (playbackController.currentlyPlayingUrl.value == episode.audioUrl) {
            if (playbackController.isPlaying.value) playbackController.pause()
            else playbackController.resume()
        } else {
            playbackController.play(episode)
        }
    }

    fun setEpisodeHeard(audioUrl: String, isHeard: Boolean) {
        viewModelScope.launch { repo.setEpisodeHeard(audioUrl, isHeard) }
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL, zero errors.

- [ ] **Step 3: Run snapshot tests**

```bash
./gradlew :app:verifyPaparazziDebug --tests "com.frybynite.podlore.ui.episodes.EpisodeRowSnapshotTest"
```

Expected: BUILD SUCCESSFUL — snapshots match (EpisodeListViewModel changes don't affect rendering).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podlore/ui/episodes/EpisodeListViewModel.kt
git commit -m "feat: migrate EpisodeListViewModel to use PlaybackController"
```

---

### Task 5: Manual smoke test

Cannot be automated — requires a device or emulator.

- [ ] **Step 1: Install debug build**

```bash
./gradlew :app:installDebug
```

- [ ] **Step 2: Test episode list play (downloaded)**

Open episode list, find a downloaded episode (status DONE). Tap play → episode plays in background without navigating away. Icon shows pause. Tap pause → pauses. Tap row → navigates to player screen; player screen shows correct state (playing/paused, correct episode).

- [ ] **Step 3: Test episode list play (not downloaded)**

Find a not-downloaded episode. Tap play → download starts, progress ring shows. When download completes, playback starts automatically. Navigate to player screen → shows correct episode playing.

- [ ] **Step 4: Test cancel-and-redirect**

Tap play on episode A (starts downloading). Before it finishes, tap play on episode B (not downloaded). Episode A's download cancels. Episode B shows progress ring. When B finishes, B plays.

- [ ] **Step 5: Test player screen unaffected**

Navigate to player screen directly from episode list row tap. Player screen plays, pauses, seeks, chapter navigation all work. Speed change persists. Sleep timer works.

- [ ] **Step 6: Test chapter pre-fetch**

Open episode list. Tap play on a not-yet-opened episode that has a `chaptersUrl`. Wait for download + playback to start. Navigate to player screen → chapters visible immediately without a loading delay.
