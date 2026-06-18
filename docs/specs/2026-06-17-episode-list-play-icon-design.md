# Episode List Play Icon

## Overview

Replace the per-episode download icon with a play/pause icon that starts background playback directly from the episode list. Tapping the rest of the row still navigates to the player screen.

## State

`EpisodeListViewModel` gains three new pieces:

```
currentlyPlayingUrl: StateFlow<String?>   // controller.currentMediaItem?.mediaId
isPlaying: StateFlow<Boolean>             // controller.isPlaying
pendingAutoPlayUrl: String? = null        // internal; episode awaiting download → auto-play
```

A `MediaController` is connected in `init` using the same `SessionToken` + `MediaController.Builder.buildAsync()` pattern as `PlayerViewModel`. A `Player.Listener` added on connect updates `currentlyPlayingUrl` via `onMediaItemTransition` and `isPlaying` via `onIsPlayingChanged`. The controller is released in `onCleared`.

The existing `downloadProgress: StateFlow<Map<String, Float>>` is unchanged.

## Row icon logic

Right-side of each `EpisodeRow` (heard episodes keep no icon, as today):

| Condition (checked in priority order) | Shown |
|---------------------------------------|-------|
| `downloadStatus == DOWNLOADING/QUEUED` **or** `audioUrl == pendingAutoPlayUrl` | Determinate `CircularProgressIndicator` with `progress = downloadProgress[audioUrl] ?: 0f` |
| `currentlyPlayingUrl == audioUrl && isPlaying` | Pause icon — tap calls `onPlayPause` |
| everything else | Play icon — tap calls `onPlayPause` |

Tap on the progress ring is a no-op.
Tap on the rest of the row navigates to the player screen (unchanged).

`EpisodeRow`'s existing `onDownload` parameter is replaced by `onPlayPause: () -> Unit`.

## Play action

`fun onPlayPause(episode: Episode)` in `EpisodeListViewModel`:

1. **Currently playing this episode** → toggle: `controller.pause()` if playing, `controller.play()` if paused.
2. **`downloadStatus == DONE`** → build `MediaItem(mediaId=audioUrl, uri=Uri.fromFile(File(downloadPath)))`, call `controller.setMediaItem(item); controller.prepare(); controller.play()`.
3. **`downloadStatus == DOWNLOADING or QUEUED`** → set `pendingAutoPlayUrl = episode.audioUrl`. Download already running; auto-play will trigger on completion.
4. **`downloadStatus == NONE`** → cancel pending download if any (`repo.cancelDownload(pendingAutoPlayUrl)`), call `repo.downloadEpisode(episode.audioUrl)`, set `pendingAutoPlayUrl = episode.audioUrl`.

## Auto-play trigger

In `init`, collect `episodes` flow. On each emission, if `pendingAutoPlayUrl != null` and the matching episode has `downloadStatus == DONE`:

```kotlin
val ep = episodes.find { it.audioUrl == pendingAutoPlayUrl } ?: return@collect
if (ep.downloadStatus == DownloadStatus.DONE) {
    pendingAutoPlayUrl = null
    val item = MediaItem.Builder()
        .setMediaId(ep.audioUrl)
        .setUri(Uri.fromFile(File(ep.downloadPath!!)))
        .build()
    controller?.setMediaItem(item)
    controller?.prepare()
    controller?.play()
}
```

## Cancel download

Add `fun cancelDownload(audioUrl: String)` to `PodcastRepository`:

```kotlin
workManager.cancelUniqueWork("download_$audioUrl")
```

No change to `DownloadWorker` — `downloadEpisode` already uses `enqueueUniqueWork("download_$audioUrl", KEEP, ...)`.

## Files changed

| File | Change |
|------|--------|
| `EpisodeListViewModel.kt` | Add `MediaController`, `currentlyPlayingUrl`, `isPlaying`, `pendingAutoPlayUrl`, `onPlayPause()` |
| `EpisodeListScreen.kt` | Replace `onDownload` with `onPlayPause`; update icon logic |
| `PodcastRepository.kt` | Add `cancelDownload(audioUrl)` |

No database schema changes.
