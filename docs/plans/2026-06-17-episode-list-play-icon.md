# Episode List Play Icon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the per-row download icon on the episode list with a play/pause icon that starts background playback without navigating away.

**Architecture:** `EpisodeListViewModel` connects its own `MediaController` to `PlaybackService`, exposing `currentlyPlayingUrl` and `isPlaying` state flows. `EpisodeRow` replaces its `onDownload` callback with `onPlayPause`, rendering a play icon, pause icon, or determinate progress ring based on play state and download status. `PodcastRepository` gains a `cancelDownload` method to cancel a pending unique work item.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Media3 `MediaController`, WorkManager, Room/Flow

## Global Constraints

- Work in worktree `worktrees/episode-list-play-icon` on branch `episode-list-play-icon`
- All Kotlin; no Java
- No database schema changes — `EpisodeEntity` and `PodcastDatabase` version unchanged
- Do not modify `PlaybackService`, `DownloadWorker`, or `PlayerViewModel`
- Follow existing import/formatting conventions in each file

---

### Task 1: Add `cancelDownload` to `PodcastRepository`

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/data/repository/PodcastRepository.kt`

**Interfaces:**
- Produces: `fun cancelDownload(audioUrl: String)` — cancels the unique work named `"download_$audioUrl"`

- [ ] **Step 1: Add `cancelDownload` after `downloadEpisode`**

Open `PodcastRepository.kt`. After the `downloadEpisode` function (line ~71), add:

```kotlin
fun cancelDownload(audioUrl: String) {
    workManager.cancelUniqueWork("download_$audioUrl")
}
```

No new imports needed — `workManager` and `cancelUniqueWork` are already used in this file.

- [ ] **Step 2: Build to verify**

```bash
cd worktrees/episode-list-play-icon
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL, zero errors.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/data/repository/PodcastRepository.kt
git commit -m "feat: add cancelDownload to PodcastRepository"
```

---

### Task 2: Add MediaController and playback state to `EpisodeListViewModel`

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/ui/episodes/EpisodeListViewModel.kt`

**Interfaces:**
- Consumes: `PodcastRepository.cancelDownload(audioUrl: String)` (Task 1), `PodcastRepository.downloadEpisode(audioUrl: String)` (already exists)
- Produces:
  - `val currentlyPlayingUrl: StateFlow<String?>` — `mediaId` of the currently loaded item, or null
  - `val isPlaying: StateFlow<Boolean>` — true while ExoPlayer is actively playing
  - `fun onPlayPause(episode: Episode)` — drives all play/download/pause logic

- [ ] **Step 1: Replace the file contents**

Replace `EpisodeListViewModel.kt` with the following:

```kotlin
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
```

- [ ] **Step 2: Build to verify**

```bash
cd worktrees/episode-list-play-icon
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL, zero errors.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/ui/episodes/EpisodeListViewModel.kt
git commit -m "feat: add MediaController and onPlayPause to EpisodeListViewModel"
```

---

### Task 3: Update `EpisodeRow` and `EpisodeListScreen` with play/pause icon

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/ui/episodes/EpisodeListScreen.kt`

**Interfaces:**
- Consumes:
  - `vm.currentlyPlayingUrl: StateFlow<String?>` (Task 2)
  - `vm.isPlaying: StateFlow<Boolean>` (Task 2)
  - `vm.onPlayPause(episode: Episode)` (Task 2)
- `EpisodeRow` signature change: `onDownload` removed, `onPlayPause: () -> Unit` added, plus two new params `isCurrentlyPlaying: Boolean` and `isPlayingActive: Boolean`

- [ ] **Step 1: Update `EpisodeListScreen` — collect new state and update `EpisodeRow` call site**

In `EpisodeListScreen`, add two new `collectAsStateWithLifecycle` lines below the existing `val downloadProgress` line:

```kotlin
val currentlyPlayingUrl by vm.currentlyPlayingUrl.collectAsStateWithLifecycle()
val isPlaying by vm.isPlaying.collectAsStateWithLifecycle()
```

Replace the `EpisodeRow(...)` call inside `items(episodes, ...)` with:

```kotlin
EpisodeRow(
    episode = episode,
    fallbackImageUrl = podcastImageUrl,
    downloadProgress = downloadProgress[episode.audioUrl],
    isCurrentlyPlaying = currentlyPlayingUrl == episode.audioUrl,
    isPlayingActive = isPlaying,
    onClick = { onEpisodeClick(episode.audioUrl) },
    onPlayPause = { vm.onPlayPause(episode) },
    onToggleHeard = { vm.setEpisodeHeard(episode.audioUrl, !episode.isHeard) }
)
```

- [ ] **Step 2: Update `EpisodeRow` signature and icon logic**

Replace the `EpisodeRow` composable signature and its icon section. New signature:

```kotlin
@Composable
internal fun EpisodeRow(
    episode: Episode,
    fallbackImageUrl: String? = null,
    downloadProgress: Float? = null,
    isCurrentlyPlaying: Boolean = false,
    isPlayingActive: Boolean = false,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onToggleHeard: () -> Unit = {}
)
```

Replace the entire trailing icon block (the `if (!episode.isHeard) { when (episode.downloadStatus) { ... } }` section) with:

```kotlin
if (!episode.isHeard) {
    val isDownloadingOrQueued = episode.downloadStatus == DownloadStatus.DOWNLOADING ||
            episode.downloadStatus == DownloadStatus.QUEUED
    when {
        isDownloadingOrQueued -> CircularProgressIndicator(
            progress = { (downloadProgress ?: 0f).coerceAtLeast(0.05f) },
            modifier = Modifier.size(48.dp).padding(end = 12.dp),
            strokeWidth = 2.dp
        )
        isCurrentlyPlaying && isPlayingActive -> IconButton(onClick = onPlayPause) {
            Icon(Icons.Filled.Pause, contentDescription = "Pause")
        }
        else -> IconButton(onClick = onPlayPause) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
        }
    }
}
```

- [ ] **Step 3: Add missing imports**

Add to the import block at the top of `EpisodeListScreen.kt`:

```kotlin
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
```

Remove unused imports: `Icons.Filled.Download`, `Icons.Filled.DownloadDone` (if no longer referenced elsewhere in the file).

- [ ] **Step 4: Build to verify**

```bash
cd worktrees/episode-list-play-icon
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL, zero errors.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/ui/episodes/EpisodeListScreen.kt
git commit -m "feat: replace download icon with play/pause in EpisodeRow"
```

---

### Task 4: Manual smoke test

No automated UI tests exist for this screen; verify manually via device or emulator.

- [ ] **Step 1: Install debug build**

```bash
cd worktrees/episode-list-play-icon
./gradlew :app:installDebug
```

- [ ] **Step 2: Test downloaded episode**

Open an episode list where at least one episode is already downloaded (status DONE).
- Verify right-side icon shows play arrow (not download icon).
- Tap play → episode starts playing in background without navigating away.
- Verify icon changes to pause on that row.
- Tap pause → playback pauses, icon reverts to play.
- Tap rest of row → navigates to player screen.

- [ ] **Step 3: Test undownloaded episode**

Find an episode with `NONE` status.
- Tap play → download starts, icon shows progress ring.
- When download completes, playback starts automatically, icon shows pause.

- [ ] **Step 4: Test cancel-and-redirect**

Start a play-download on episode A (shows progress ring). Before it finishes, tap play on episode B (NONE status).
- Episode A's download should cancel (progress ring disappears from A's row).
- Episode B shows progress ring.
- When B finishes downloading, B plays automatically.

- [ ] **Step 5: Final commit if any fixups made**

```bash
git add -p
git commit -m "fix: <describe any fixup>"
```
