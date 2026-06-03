# Progress Bar with Chapter Markers — Design

## Goal

Replace the absent seek UI with a full-featured audio progress bar: current time display, chapter marker indicators, draggable seek with two modes (free drag vs snap-to-chapter via long-press).

## Layout

```
[ 0:42 ] [====|---|---------|---  ] [ 48:12 ]
```

- Left label: current playback position (`formatMs`)
- Right label: total duration (`formatMs`)
- Bar: custom Canvas composable spanning between labels

## Canvas Drawing

- **Track**: rounded rect, `surfaceVariant` color, 6dp tall
- **Fill**: rounded rect over track, `primary` color, width = `(positionMs / durationMs) * totalWidth`
- **Chapter markers**: 2dp wide × 16dp tall rect at each chapter's `(startTimeMs / durationMs) * totalWidth`; first chapter (position 0) skipped; markers behind playhead tinted primary at 70% alpha, ahead at `onSurface` 50% alpha
- **Thumb**: circle, 8dp radius normally; 12dp radius in snap mode, color shifts to `tertiary`

## Gesture Logic (`awaitEachGesture`)

```
DOWN received
  → start 500ms timer coroutine
  → record down.x as drag position

MOVE received before timer fires
  → cancel timer
  → enter FREE DRAG mode
  → subsequent moves update drag position freely

Timer fires (no prior MOVE)
  → enter SNAP mode
  → haptic: HapticFeedbackType.LongPress
  → thumb grows + color shifts
  → subsequent moves update drag position, snapping to nearest chapter if within threshold

UP received
  → if SNAP mode: snapToChapter(dragMs, threshold=10s)
  → if FREE mode: seek to raw drag position
  → reset state
```

Key invariant: timer is cancelled the moment any `MOVE` is detected — long-press cannot activate mid-drag.

## Snap Logic

```kotlin
fun snapToChapter(rawMs: Long, chapters: List<Chapter>, thresholdMs: Long = 10_000L): Long {
    val nearest = chapters.minByOrNull { abs(it.startTimeMs - rawMs) } ?: return rawMs
    return if (abs(nearest.startTimeMs - rawMs) <= thresholdMs) nearest.startTimeMs else rawMs
}
```

## PlayerViewModel Changes

Add two new StateFlows:
- `currentPositionMs: StateFlow<Long>` — updated in `updateCurrentChapterIndex()` each tick
- `durationMs: StateFlow<Long>` — updated same place from `controller?.duration`

## New File

`app/src/main/kotlin/com/podcastapp/ui/player/ChapterProgressBar.kt`

## PlayerScreen Changes

- Collect `currentPositionMs`, `durationMs`
- Replace placeholder (none currently) with:
  ```
  Row(time label | ChapterProgressBar | duration label)
  ```
  placed between artwork and playback controls

## Haptics Backlog

`TODO: expose HapticsPreference setting to disable haptic on snap activation`

## Out of Scope

- Waveform visualization
- Chapter thumbnail previews on drag
- Buffering indicator
