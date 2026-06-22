# Global PlaybackController

## Overview

Replace the per-ViewModel `MediaController` pattern with a single `@Singleton PlaybackController` that owns the one `MediaController` for the app, exposes playback state as `StateFlow`s, and pre-fetches chapter data when the playing episode changes.

## Motivation

`PlayerViewModel` and `EpisodeListViewModel` each build their own `MediaController` and add their own `Player.Listener`. This is redundant — both connect to the same `PlaybackService`. A shared controller eliminates duplicate connections, provides a single source of truth for play state across all screens, and enables pre-fetching chapters before the player screen opens.

## New Component: PlaybackController

**File:** `app/src/main/kotlin/com/frybynite/podlore/playback/PlaybackController.kt`

```kotlin
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val episodeDao: EpisodeDao,
    private val podcastRepo: PodcastRepository,
    private val chapterRepo: ChapterRepository
)
```

### State

```kotlin
val currentlyPlayingUrl: StateFlow<String?>   // null when nothing loaded
val isPlaying: StateFlow<Boolean>
val controller: MediaController?              // exposed for seekTo, custom commands, speed
```

`controller` starts null and becomes non-null after the async `buildAsync()` future resolves. Callers already null-check (same pattern as today's ViewModels).

### Lifecycle

Connects once in `init`:

```kotlin
val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
val future = MediaController.Builder(context, token).buildAsync()
future.addListener({
    runCatching {
        controller = future.get()
        controller?.addListener(listener)
        _isPlaying.value = controller?.isPlaying ?: false
        _currentlyPlayingUrl.value = controller?.currentMediaItem?.mediaId
    }
}, { it.run() })
```

Uses an internal `CoroutineScope(SupervisorJob() + Dispatchers.Main)` — same pattern as `PlaybackService.serviceScope`. No `onCleared()` — singleton lives for the app process lifetime.

### Commands

```kotlin
fun play(episode: Episode)
fun pause()
fun resume()
```

`play(episode)`:
- If `episode.downloadStatus == DONE` → `MediaItem` with `file://` URI from `downloadPath`
- If `DOWNLOADING` or `QUEUED` → set `pendingAutoPlayUrl = episode.audioUrl`
- If `NONE` → cancel prior `pendingAutoPlayUrl` download, call `podcastRepo.downloadEpisode()`, set `pendingAutoPlayUrl`

`pendingAutoPlayUrl` auto-play trigger: observe `episodeDao.getByAudioUrlFlow(audioUrl)` — when pending episode flips to `DONE`, build `MediaItem` and play. (This logic moves from `EpisodeListViewModel`. Uses `episodeDao` directly since `PlaybackController` knows the `audioUrl` but not the `feedUrl` needed for `podcastRepo.episodesForPodcast()`.)

### Pre-fetch on episode change

In the `Player.Listener.onMediaItemTransition` callback:

```kotlin
scope.launch {
    val ep = episodeDao.getByAudioUrl(audioUrl) ?: return@launch
    ep.chaptersUrl?.let { chapterRepo.fetchAndCacheChapters(audioUrl, it) }
}
```

Chapters land in Room; `PlayerViewModel`'s `chaptersForEpisode()` flow picks them up. No changes to `PlayerViewModel`'s chapter handling.

### Hilt wiring

New module `PlaybackModule.kt` in `app/.../playback/`:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {
    // PlaybackController is @Singleton + @Inject constructor — no explicit @Provides needed
    // Module exists to group playback-related bindings if added later
}
```

`PlaybackController` uses `@Inject constructor` so Hilt provides it automatically at `@Singleton` scope.

## PlayerViewModel migration

**File:** `app/src/main/kotlin/com/frybynite/podlore/ui/player/PlayerViewModel.kt`

### Remove
- `var controller: MediaController?` field and private setter
- MediaController build code inside `connect(audioUrl)` (~25 lines)
- `controller?.release()` from `onCleared()`
- `Player.Listener` anonymous class that was managing `_isPlaying` and position tracking

### Add
- `private val playbackController: PlaybackController` constructor param (Hilt-injected)

### Keep `connect(audioUrl)` — slim it down to
1. Load episode metadata from DB (title, artwork, saved position) — unchanged
2. Subscribe to `chaptersForEpisode()` flow — unchanged
3. Derive `_isPlaying` from `playbackController.isPlaying` (collect in viewModelScope)
4. Derive current media item transitions from `playbackController.currentlyPlayingUrl`
5. Call `playbackController.play(episode)` if the episode isn't already loaded

### Direct controller access
All `controller.seekTo()`, `controller.setPlaybackParameters()`, `controller.sendCustomCommand()` calls use `playbackController.controller` — no behavior change.

## EpisodeListViewModel migration

**File:** `app/src/main/kotlin/com/frybynite/podlore/ui/episodes/EpisodeListViewModel.kt`

### Remove
- `@ApplicationContext context` constructor param
- `controller: MediaController?` field
- MediaController `init` block (connect, listener, initial sync)
- `onCleared()` controller release
- Local `_currentlyPlayingUrl` and `_isPlaying` MutableStateFlows
- `pendingAutoPlayUrl` and its auto-play trigger (moved to `PlaybackController`)
- `onPlayPause` MediaItem-building logic

### Add
- `private val playbackController: PlaybackController` constructor param

### Keep as delegating properties
```kotlin
val currentlyPlayingUrl = playbackController.currentlyPlayingUrl
val isPlaying = playbackController.isPlaying
```

### `onPlayPause(episode)` becomes
```kotlin
fun onPlayPause(episode: Episode) {
    if (playbackController.currentlyPlayingUrl.value == episode.audioUrl) {
        if (playbackController.isPlaying.value) playbackController.pause()
        else playbackController.resume()
    } else {
        playbackController.play(episode)
    }
}
```

## Branch strategy

New branch `global-playback-controller` off `episode-list-play-icon`. New worktree at `worktrees/global-playback-controller`.

The `episode-list-play-icon` worktree remains intact for device testing. When `global-playback-controller` is ready, merge directly to `master` (it contains all `episode-list-play-icon` commits).

## Files changed

| File | Change |
|------|--------|
| `playback/PlaybackController.kt` | New — owns MediaController, state, play commands, pre-fetch |
| `playback/PlaybackModule.kt` | New — Hilt module (placeholder for future bindings) |
| `ui/player/PlayerViewModel.kt` | Remove MediaController ownership; inject PlaybackController |
| `ui/episodes/EpisodeListViewModel.kt` | Remove MediaController; delegate to PlaybackController |
| `data/db/dao/EpisodeDao.kt` | Add `fun getByAudioUrlFlow(audioUrl: String): Flow<EpisodeEntity?>` for pending auto-play observation |

No database schema changes.
