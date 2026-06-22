# Progress Bar with Chapter Markers — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a full-featured audio progress bar to PlayerScreen with chapter markers, current time display, free-drag seek, and long-press snap-to-chapter mode.

**Architecture:** Three changes: (1) expose `currentPositionMs` + `durationMs` from `PlayerViewModel`; (2) new `ChapterProgressBar` composable with Canvas drawing and raw pointer gesture handling; (3) wire the bar into `PlayerScreen` between artwork and controls.

**Tech Stack:** Kotlin · Jetpack Compose · Canvas · `awaitEachGesture` · `LocalHapticFeedback`

---

## Task 1: Expose position + duration from PlayerViewModel

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podlore/ui/player/PlayerViewModel.kt`
- Test: `app/src/test/kotlin/com/frybynite/podlore/ui/player/PlayerViewModelPositionTest.kt`

**Step 1: Write failing test**

`app/src/test/kotlin/com/frybynite/podlore/ui/player/PlayerViewModelPositionTest.kt`:
```kotlin
package com.frybynite.podlore.ui.player

import com.frybynite.podlore.data.db.dao.EpisodeDao
import com.frybynite.podlore.data.db.dao.PodcastDao
import com.frybynite.podlore.data.preferences.SpeedPreferences
import com.frybynite.podlore.data.repository.ChapterRepository
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelPositionTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `currentPositionMs defaults to zero`() {
        val vm = PlayerViewModel(
            context = mockk(relaxed = true),
            chapterRepo = mockk(relaxed = true),
            episodeDao = mockk(relaxed = true),
            podcastDao = mockk(relaxed = true),
            speedPrefs = mockk(relaxed = true) { io.mockk.every { speed } returns 1f }
        )
        assertEquals(0L, vm.currentPositionMs.value)
    }

    @Test fun `durationMs defaults to zero`() {
        val vm = PlayerViewModel(
            context = mockk(relaxed = true),
            chapterRepo = mockk(relaxed = true),
            episodeDao = mockk(relaxed = true),
            podcastDao = mockk(relaxed = true),
            speedPrefs = mockk(relaxed = true) { io.mockk.every { speed } returns 1f }
        )
        assertEquals(0L, vm.durationMs.value)
    }
}
```

**Step 2: Run — expect FAIL**
```bash
./gradlew :app:test --tests "com.frybynite.podlore.ui.player.PlayerViewModelPositionTest"
```
Expected: `Unresolved reference: currentPositionMs` / `durationMs`

**Step 3: Add StateFlows to PlayerViewModel**

In `PlayerViewModel.kt`, after `_isPlaying`:
```kotlin
private val _currentPositionMs = MutableStateFlow(0L)
val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

private val _durationMs = MutableStateFlow(0L)
val durationMs: StateFlow<Long> = _durationMs.asStateFlow()
```

Update `updateCurrentChapterIndex()` — replace existing body with:
```kotlin
fun updateCurrentChapterIndex() {
    val pos = controller?.currentPosition ?: return
    val dur = controller?.duration?.takeIf { it > 0 } ?: 0L
    _currentPositionMs.value = pos
    _durationMs.value = dur
    val idx = _chapters.value.indexOfLast { it.startTimeMs <= pos }
    if (idx >= 0) _currentChapterIndex.value = idx
}
```

**Step 4: Run — expect PASS**
```bash
./gradlew :app:test --tests "com.frybynite.podlore.ui.player.PlayerViewModelPositionTest"
```

**Step 5: Commit**
```bash
git add app/src/main/kotlin/com/frybynite/podlore/ui/player/PlayerViewModel.kt \
        app/src/test/kotlin/com/frybynite/podlore/ui/player/PlayerViewModelPositionTest.kt
git commit -m "feat: expose currentPositionMs and durationMs from PlayerViewModel"
```

---

## Task 2: snapToChapter utility (pure function, TDD)

**Files:**
- Create: `app/src/main/kotlin/com/frybynite/podlore/ui/player/ChapterProgressBar.kt` (stub + function)
- Test: `app/src/test/kotlin/com/frybynite/podlore/ui/player/SnapToChapterTest.kt`

**Step 1: Write failing test**

`app/src/test/kotlin/com/frybynite/podlore/ui/player/SnapToChapterTest.kt`:
```kotlin
package com.frybynite.podlore.ui.player

import com.frybynite.podlore.domain.model.Chapter
import org.junit.Test
import kotlin.test.assertEquals

class SnapToChapterTest {
    private val chapters = listOf(
        Chapter(episodeAudioUrl = "u", startTimeMs = 0,      endTimeMs = 20_000,  title = "Intro", url = null),
        Chapter(episodeAudioUrl = "u", startTimeMs = 20_000, endTimeMs = 60_000,  title = "Ch 2",  url = null),
        Chapter(episodeAudioUrl = "u", startTimeMs = 60_000, endTimeMs = 120_000, title = "Ch 3",  url = null)
    )

    @Test fun `within threshold snaps to nearest chapter`() {
        // 7s past Ch 2 start — within 10s threshold
        assertEquals(20_000L, snapToChapter(27_000L, chapters))
    }

    @Test fun `beyond threshold returns raw position`() {
        // 15s past Ch 2 start — beyond 10s threshold
        assertEquals(35_000L, snapToChapter(35_000L, chapters))
    }

    @Test fun `snaps to chapter 0 when near start`() {
        assertEquals(0L, snapToChapter(5_000L, chapters))
    }

    @Test fun `snaps to last chapter when near its start`() {
        assertEquals(60_000L, snapToChapter(65_000L, chapters))
    }

    @Test fun `empty chapters returns raw position`() {
        assertEquals(42_000L, snapToChapter(42_000L, emptyList()))
    }

    @Test fun `exact chapter start returns that start`() {
        assertEquals(20_000L, snapToChapter(20_000L, chapters))
    }
}
```

**Step 2: Run — expect FAIL**
```bash
./gradlew :app:test --tests "com.frybynite.podlore.ui.player.SnapToChapterTest"
```
Expected: `Unresolved reference: snapToChapter`

**Step 3: Create ChapterProgressBar.kt with snapToChapter**

`app/src/main/kotlin/com/frybynite/podlore/ui/player/ChapterProgressBar.kt`:
```kotlin
package com.frybynite.podlore.ui.player

import com.frybynite.podlore.domain.model.Chapter
import kotlin.math.abs

internal fun snapToChapter(
    rawMs: Long,
    chapters: List<Chapter>,
    thresholdMs: Long = 10_000L
): Long {
    val nearest = chapters.minByOrNull { abs(it.startTimeMs - rawMs) } ?: return rawMs
    return if (abs(nearest.startTimeMs - rawMs) <= thresholdMs) nearest.startTimeMs else rawMs
}
```

**Step 4: Run — expect PASS**
```bash
./gradlew :app:test --tests "com.frybynite.podlore.ui.player.SnapToChapterTest"
```

**Step 5: Commit**
```bash
git add app/src/main/kotlin/com/frybynite/podlore/ui/player/ChapterProgressBar.kt \
        app/src/test/kotlin/com/frybynite/podlore/ui/player/SnapToChapterTest.kt
git commit -m "feat: snapToChapter utility with TDD"
```

---

## Task 3: ChapterProgressBar composable

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podlore/ui/player/ChapterProgressBar.kt`

**Step 1: Replace stub with full composable**

```kotlin
package com.frybynite.podlore.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.frybynite.podlore.domain.model.Chapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

internal fun snapToChapter(
    rawMs: Long,
    chapters: List<Chapter>,
    thresholdMs: Long = 10_000L
): Long {
    val nearest = chapters.minByOrNull { abs(it.startTimeMs - rawMs) } ?: return rawMs
    return if (abs(nearest.startTimeMs - rawMs) <= thresholdMs) nearest.startTimeMs else rawMs
}

private enum class DragMode { NONE, FREE, SNAP }

@Composable
fun ChapterProgressBar(
    positionMs: Long,
    durationMs: Long,
    chapters: List<Chapter>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary
    val markerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val markerActiveColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    val snapColor = MaterialTheme.colorScheme.tertiary

    var dragMode by remember { mutableStateOf(DragMode.NONE) }
    var dragFraction by remember { mutableStateOf(0f) }

    val displayFraction = when {
        dragMode != DragMode.NONE -> dragFraction
        durationMs > 0 -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        else -> 0f
    }

    val thumbRadius = if (dragMode == DragMode.SNAP) 12.dp else 8.dp
    val thumbColor = if (dragMode == DragMode.SNAP) snapColor else progressColor

    Canvas(
        modifier = modifier
            .height(40.dp)
            .pointerInput(durationMs, chapters) {
                var snapTimerJob: Job? = null
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (durationMs <= 0) return@awaitEachGesture

                    dragMode = DragMode.NONE
                    dragFraction = (down.position.x / size.width).coerceIn(0f, 1f)

                    snapTimerJob = launch {
                        delay(500L)
                        // Timer fired — no move detected yet
                        dragMode = DragMode.SNAP
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }

                    var moved = false
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        val newFraction = (change.position.x / size.width).coerceIn(0f, 1f)

                        if (!moved && newFraction != dragFraction) {
                            moved = true
                            snapTimerJob?.cancel()
                            if (dragMode == DragMode.NONE) dragMode = DragMode.FREE
                        }

                        dragFraction = if (dragMode == DragMode.SNAP) {
                            val rawMs = (newFraction * durationMs).toLong()
                            val snappedMs = snapToChapter(rawMs, chapters)
                            (snappedMs.toFloat() / durationMs).coerceIn(0f, 1f)
                        } else {
                            newFraction
                        }

                        change.consume()
                    } while (event.changes.any { it.pressed })

                    snapTimerJob?.cancel()

                    val seekMs = (dragFraction * durationMs).toLong()
                    dragMode = DragMode.NONE
                    onSeek(seekMs)
                }
            }
    ) {
        val trackH = 6.dp.toPx()
        val markerW = 2.dp.toPx()
        val markerH = 16.dp.toPx()
        val cy = size.height / 2f
        val trackTop = cy - trackH / 2f
        val thumbR = thumbRadius.toPx()

        // Background track
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, trackTop),
            size = Size(size.width, trackH),
            cornerRadius = CornerRadius(trackH / 2f)
        )

        // Progress fill
        drawRoundRect(
            color = progressColor,
            topLeft = Offset(0f, trackTop),
            size = Size((size.width * displayFraction).coerceAtLeast(0f), trackH),
            cornerRadius = CornerRadius(trackH / 2f)
        )

        // Chapter markers (skip index 0 — that's the track start)
        if (durationMs > 0) {
            chapters.drop(1).forEach { chapter ->
                val x = (chapter.startTimeMs.toFloat() / durationMs) * size.width
                val behind = chapter.startTimeMs <= (displayFraction * durationMs).toLong()
                drawRect(
                    color = if (behind) markerActiveColor else markerColor,
                    topLeft = Offset(x - markerW / 2f, cy - markerH / 2f),
                    size = Size(markerW, markerH)
                )
            }
        }

        // Seek thumb (only when dragging)
        if (dragMode != DragMode.NONE) {
            drawCircle(
                color = thumbColor,
                radius = thumbR,
                center = Offset(size.width * displayFraction, cy)
            )
        }
    }
}
```

**Step 2: Compile check**
```bash
./gradlew :app:kspDebugKotlin 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

**Step 3: Commit**
```bash
git add app/src/main/kotlin/com/frybynite/podlore/ui/player/ChapterProgressBar.kt
git commit -m "feat: ChapterProgressBar composable with free-drag and long-press snap mode"
```

---

## Task 4: Wire progress bar into PlayerScreen

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podlore/ui/player/PlayerScreen.kt`

**Step 1: Collect new state**

In `PlayerScreen`, after the existing `collectAsStateWithLifecycle` calls, add:
```kotlin
val currentPositionMs by vm.currentPositionMs.collectAsStateWithLifecycle()
val durationMs by vm.durationMs.collectAsStateWithLifecycle()
```

**Step 2: Add progress row**

In the `Column` inside `Scaffold`, place this block between the artwork `Box` and the playback controls `Row` (replace the existing `Spacer(Modifier.height(8.dp))` between them):

```kotlin
Spacer(Modifier.height(12.dp))

Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
) {
    Text(
        text = formatMs(currentPositionMs),
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.width(48.dp)
    )
    ChapterProgressBar(
        positionMs = currentPositionMs,
        durationMs = durationMs,
        chapters = chapters,
        onSeek = { ms -> vm.controller?.seekTo(ms) },
        modifier = Modifier.weight(1f)
    )
    Text(
        text = formatMs(durationMs),
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.width(48.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.End
    )
}

Spacer(Modifier.height(4.dp))
```

**Step 3: Compile check**
```bash
./gradlew :app:assembleDebug 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

**Step 4: Run all tests**
```bash
./gradlew :app:test
```
Expected: all pass.

**Step 5: Commit**
```bash
git add app/src/main/kotlin/com/frybynite/podlore/ui/player/PlayerScreen.kt
git commit -m "feat: wire ChapterProgressBar into PlayerScreen with time labels"
```

---

## Task 5: Deploy and verify

**Step 1: Deploy to emulator**
```bash
./scripts/emulator.sh
```

**Step 2: Manual checklist**
- [ ] Progress bar visible between artwork and controls
- [ ] Bar fills left-to-right as audio plays
- [ ] Chapter markers appear as vertical lines at correct positions
- [ ] Markers behind playhead are primary-tinted; ahead are grey
- [ ] Tap bar → seeks to tapped position
- [ ] Drag freely → thumb appears, seeks on release
- [ ] Hold without moving ~0.5s → thumb grows + turns tertiary color + haptic fires
- [ ] Long-press then drag → position snaps to nearest chapter within 10s
- [ ] Long-press drag beyond 10s from any chapter → no snap, seeks to raw position
- [ ] Move first then hold → no snap mode activates
- [ ] Time labels update while playing

**Step 3: Commit fix + push**
```bash
git push
```
