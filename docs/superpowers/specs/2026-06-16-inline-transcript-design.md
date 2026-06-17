# Inline Transcript in Chapter List — Design Spec

**Date:** 2026-06-16

## Goal

Replace the `TranscriptPanel` `ModalBottomSheet` with inline sentence rows rendered directly inside the chapter list `LazyColumn`. Chapters remain visually unchanged; transcript sentences appear beneath each chapter's row when the transcript toggle is on.

## Current State

- `TranscriptPanel.kt` — `ModalBottomSheet` composable, shown when `showTranscript == true`
- Chapter list — `LazyColumn` in `PlayerScreen.kt` using `itemsIndexed(chapters)`
- `PlayerViewModel` — exposes `transcriptSegments`, `activeSegmentIndex`, `showTranscript`, `hasTranscript`, `transcriptLoading`

## Design

### Visual Structure

```
┌─────────────────────────────────────┐
│ 0:00  Introduction          [link]  │  ← chapter row (unchanged, existing highlight)
├─────────────────────────────────────┤
│   Welcome to AI Daily Radar...      │  ← sentence (surface bg)
├╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌┤
│   Today we have twelve stories.     │  ← active sentence (primaryContainer bg)
├─────────────────────────────────────┤
│ 0:17  OpenAI News           [link]  │  ← chapter row
├─────────────────────────────────────┤
│   From TLDR newsletter...           │
```

- Chapter rows: unchanged. Existing active/snap/surface highlight logic untouched.
- Sentence rows: indented 52dp left (aligns with chapter title, skips timestamp column). `bodySmall` text. No drag/reveal.
- Active sentence: `primaryContainer` background, `onPrimaryContainer` text. Same colors as current `TranscriptPanel`.
- Inactive sentence: `surface` background, `onSurface` text.
- Light `HorizontalDivider` between sentences (not after last sentence in a chapter).
- Tap sentence → `vm.seekToSegment(segment)`.

### Segment-to-Chapter Assignment

A segment belongs to chapter `i` if:
```
segment.startTimeSec >= chapter.startTimeMs / 1000f
  && segment.startTimeSec < nextChapter.startTimeMs / 1000f
```
Last chapter gets all remaining segments. Segments before the first chapter start time are assigned to chapter 0.

Assignment computed in `PlayerScreen` (not ViewModel) — local `val` derived from `transcriptSegments` and `chapters`, recomputed when either changes.

### Auto-scroll

`LaunchedEffect(activeSegmentIndex)` on the existing `chapterListState`. Compute the `LazyList` item index of the active segment (accounting for chapter rows and preceding sentence rows) and call `chapterListState.animateScrollToItem(itemIndex)`.

### Loading State

While `transcriptLoading == true`, a single `CircularProgressIndicator` row is inserted after the first chapter row (or at the top of the chapter list if chapters haven't loaded yet).

### Toggle

Article `IconButton` in `TopAppBar` remains. `vm.toggleTranscript()` unchanged. When `showTranscript` goes false, sentence rows disappear; chapter list returns to normal height.

## Files Changed

| File | Change |
|------|--------|
| `ui/player/PlayerScreen.kt` | Add sentence rows inside `itemsIndexed(chapters)` block; add loading row; auto-scroll `LaunchedEffect` |
| `ui/player/TranscriptPanel.kt` | **Delete** |

No ViewModel changes. No new files.

## What Doesn't Change

- `PlayerViewModel` transcript state, `toggleTranscript`, `seekToSegment`, `activeSegmentIndex` — all unchanged
- Chapter row drag/reveal/highlight behavior — untouched
- Deep dive "More About This…" row injection — untouched
- TopAppBar Article icon button — unchanged
- Transcript fetch, caching, RSS parsing — unchanged
