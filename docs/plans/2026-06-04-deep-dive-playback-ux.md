# Deep Dive Playback UX — Requirements & Implementation Plan

## Requirements

### R1 — Chapter ticks hidden during deep dive playback
While `DeepDiveState.Playing`, pass an empty chapter list to `ChapterProgressBar` so tick marks disappear. Ticks restore automatically when state returns to `Idle`.

### R2 — Chapter index frozen during deep dive playback
While `DeepDiveState.Playing`, `updateCurrentChapterIndex()` must not update `_currentChapterIndex`. The highlighted chapter row stays fixed at the chapter that triggered the deep dive. Position/duration still update (for progress bar).

### R3 — Pause, seek ±10s/30s work normally on TTS audio
No change needed — these already call `controller?.pause()` / `controller?.seekTo()` which operate on whatever is currently playing.

### R4 — Back segment (SkipPrevious) during deep dive: restart TTS
When `DeepDiveState.Playing`, clicking SkipPrevious restarts the TTS audio from the beginning: `controller?.seekTo(0)`. Normal `prevChapter()` behaviour outside deep dive is unchanged.

### R5 — Forward segment (SkipNext) during deep dive: skip deep dive
When `DeepDiveState.Playing`, clicking SkipNext calls `vm.skipDeepDive()`:
1. Cancel any in-progress exit tone job.
2. Pause controller.
3. Play exit tone (`TONE_PROP_BEEP2`, 300ms), delay 400ms.
4. Restore main episode via `setMediaItem` with `ClippingConfiguration.startPositionMs = deepDiveResumePositionMs`.
5. `prepare()` + `play()`.
6. Delete `pendingTtsFile`, state → `Idle`.

### R6 — Chapter tap during deep dive: end deep dive, jump to tapped chapter
When `DeepDiveState.Playing`, tapping a chapter row calls `vm.jumpToChapter(startTimeMs)`:
1. Cancel exit tone job (no audio cue — immediate transition).
2. Restore main episode via `setMediaItem` with `ClippingConfiguration.startPositionMs = chapter.startTimeMs`.
3. `prepare()` + `play()`.
4. Delete `pendingTtsFile`, state → `Idle`.

---

## Implementation Tasks

### Task 1 — Persist resume position and episode URI as ViewModel fields
**File:** `PlayerViewModel.kt`

Promote `savedPositionMs` and `episodeUri` from local vars inside `moreAboutThis()` to class fields:
```kotlin
private var deepDiveResumePositionMs: Long = 0L
private var deepDiveResumeEpisodeUri: String? = null
```
Assign them before setting `Loading` state. Use them when building the `resumeItem` (unchanged logic).

### Task 2 — Freeze chapter index during deep dive (R2)
**File:** `PlayerViewModel.kt` — `updateCurrentChapterIndex()`

Add early return at top of position-update logic if `_deepDiveState.value == DeepDiveState.Playing`:
```kotlin
fun updateCurrentChapterIndex() {
    val pos = controller?.currentPosition ?: return
    val dur = controller?.duration?.takeIf { it > 0 } ?: 0L
    _currentPositionMs.value = pos
    _durationMs.value = dur
    if (_deepDiveState.value == DeepDiveState.Playing) return  // freeze chapter
    val idx = _chapters.value.indexOfLast { it.startTimeMs <= pos }
    ...
}
```

### Task 3 — `skipDeepDive()` (R5)
**File:** `PlayerViewModel.kt`

```kotlin
fun skipDeepDive() {
    if (_deepDiveState.value != DeepDiveState.Playing) return
    val resumePos = deepDiveResumePositionMs
    val episodeUri = deepDiveResumeEpisodeUri ?: return
    exitToneJob?.cancel()
    controller?.pause()
    exitToneJob = viewModelScope.launch {
        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
        delay(400)
        val resumeItem = androidx.media3.common.MediaItem.Builder()
            .setMediaId(episodeUri)
            .setUri(episodeUri)
            .setClippingConfiguration(
                androidx.media3.common.MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(resumePos)
                    .build()
            )
            .build()
        controller?.setMediaItem(resumeItem)
        controller?.prepare()
        controller?.play()
        pendingTtsFile?.delete()
        pendingTtsFile = null
        _deepDiveState.value = DeepDiveState.Idle
        exitToneJob = null
    }
}
```

### Task 4 — `jumpToChapter(startTimeMs)` (R6)
**File:** `PlayerViewModel.kt`

```kotlin
fun jumpToChapter(startTimeMs: Long) {
    if (_deepDiveState.value != DeepDiveState.Playing) {
        controller?.seekTo(startTimeMs)
        return
    }
    exitToneJob?.cancel()
    exitToneJob = null
    val episodeUri = deepDiveResumeEpisodeUri ?: return
    val resumeItem = androidx.media3.common.MediaItem.Builder()
        .setMediaId(episodeUri)
        .setUri(episodeUri)
        .setClippingConfiguration(
            androidx.media3.common.MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startTimeMs)
                .build()
        )
        .build()
    controller?.setMediaItem(resumeItem)
    controller?.prepare()
    controller?.play()
    pendingTtsFile?.delete()
    pendingTtsFile = null
    _deepDiveState.value = DeepDiveState.Idle
}
```

### Task 5 — Wire up UI (R1, R3, R4, R5, R6)
**File:** `PlayerScreen.kt`

**R1 — Hide ticks:**
```kotlin
ChapterProgressBar(
    chapters = if (deepDiveState is DeepDiveState.Playing) emptyList() else chapters,
    ...
)
```

**R4 — Back segment:**
```kotlin
IconButton(onClick = {
    if (deepDiveState is DeepDiveState.Playing) controller?.seekTo(0)
    else vm.prevChapter()
})
```

**R5 — Forward segment:**
```kotlin
IconButton(onClick = {
    if (deepDiveState is DeepDiveState.Playing) vm.skipDeepDive()
    else vm.nextChapter()
})
```

**R6 — Chapter tap:**
```kotlin
.combinedClickable(
    onClick = { vm.jumpToChapter(chapter.startTimeMs) },
    onLongClick = { showMenu = true }
)
```

### Task 6 — Build verification
```bash
./gradlew :app:assembleDebug
```
