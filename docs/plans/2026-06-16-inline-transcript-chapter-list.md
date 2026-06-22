# Inline Transcript in Chapter List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the `TranscriptPanel` bottom sheet with sentence rows rendered inline below each chapter row in the chapter list `LazyColumn`.

**Architecture:** Two tasks — a pure testable utility function (`segmentsForChapter`) extracted to `TranscriptUtils.kt`, then the `PlayerScreen` edit that uses it. `TranscriptPanel.kt` is deleted. No ViewModel changes.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, JUnit4.

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/kotlin/com/frybynite/podlore/ui/player/TranscriptUtils.kt` | Create | Pure function: filter transcript segments by chapter time range |
| `app/src/main/kotlin/com/frybynite/podlore/ui/player/PlayerScreen.kt` | Modify | Remove `TranscriptPanel` block; add inline sentence rows + loading row + auto-scroll |
| `app/src/main/kotlin/com/frybynite/podlore/ui/player/TranscriptPanel.kt` | Delete | No longer needed |
| `app/src/test/kotlin/com/frybynite/podlore/ui/player/TranscriptUtilsTest.kt` | Create | Unit tests for `segmentsForChapter` |

---

## Task 1: `TranscriptUtils.kt` — segment-to-chapter mapping

**Files:**
- Create: `app/src/main/kotlin/com/frybynite/podlore/ui/player/TranscriptUtils.kt`
- Create: `app/src/test/kotlin/com/frybynite/podlore/ui/player/TranscriptUtilsTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/kotlin/com/frybynite/podlore/ui/player/TranscriptUtilsTest.kt`:

```kotlin
package com.frybynite.podlore.ui.player

import com.frybynite.podlore.domain.model.TranscriptSegment
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranscriptUtilsTest {
    private fun seg(start: Float, end: Float, text: String = "text") =
        TranscriptSegment(start, end, text)

    @Test fun `returns segments within chapter range`() {
        val segments = listOf(seg(0f, 3f), seg(3f, 7f), seg(17f, 25f))
        val result = segmentsForChapter(segments, 0f, 17f)
        assertEquals(2, result.size)
        assertEquals(0f, result[0].startTimeSec)
        assertEquals(3f, result[1].startTimeSec)
    }

    @Test fun `excludes segment at next chapter start boundary`() {
        val segments = listOf(seg(10f, 15f), seg(17f, 25f))
        val result = segmentsForChapter(segments, 0f, 17f)
        assertEquals(1, result.size)
        assertEquals(10f, result[0].startTimeSec)
    }

    @Test fun `last chapter captures all remaining segments via MAX_VALUE`() {
        val segments = listOf(seg(17f, 25f), seg(25f, 40f))
        val result = segmentsForChapter(segments, 17f, Float.MAX_VALUE)
        assertEquals(2, result.size)
    }

    @Test fun `returns empty for chapter with no segments in range`() {
        val segments = listOf(seg(0f, 3f), seg(3f, 7f))
        val result = segmentsForChapter(segments, 50f, 100f)
        assertTrue(result.isEmpty())
    }

    @Test fun `empty segment list returns empty`() {
        val result = segmentsForChapter(emptyList(), 0f, 100f)
        assertTrue(result.isEmpty())
    }

    @Test fun `segment exactly at chapter start is included`() {
        val segments = listOf(seg(17f, 25f))
        val result = segmentsForChapter(segments, 17f, 30f)
        assertEquals(1, result.size)
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```
./gradlew :app:testDebugUnitTest --tests "com.frybynite.podlore.ui.player.TranscriptUtilsTest" 2>&1 | tail -10
```

Expected: compilation error — `segmentsForChapter` not found.

- [ ] **Step 3: Create `TranscriptUtils.kt`**

Create `app/src/main/kotlin/com/frybynite/podlore/ui/player/TranscriptUtils.kt`:

```kotlin
package com.frybynite.podlore.ui.player

import com.frybynite.podlore.domain.model.TranscriptSegment

fun segmentsForChapter(
    segments: List<TranscriptSegment>,
    chapterStartSec: Float,
    nextChapterStartSec: Float
): List<TranscriptSegment> =
    segments.filter { it.startTimeSec >= chapterStartSec && it.startTimeSec < nextChapterStartSec }
```

- [ ] **Step 4: Run tests to confirm they pass**

```
./gradlew :app:testDebugUnitTest --tests "com.frybynite.podlore.ui.player.TranscriptUtilsTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podlore/ui/player/TranscriptUtils.kt
git add app/src/test/kotlin/com/frybynite/podlore/ui/player/TranscriptUtilsTest.kt
git commit -m "feat: add segmentsForChapter utility for inline transcript chapter grouping"
```

---

## Task 2: Inline sentences in `PlayerScreen` + delete `TranscriptPanel`

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podlore/ui/player/PlayerScreen.kt`
- Delete: `app/src/main/kotlin/com/frybynite/podlore/ui/player/TranscriptPanel.kt`

No unit tests for this task (pure Compose UI). Verified by successful build.

- [ ] **Step 1: Read `PlayerScreen.kt` in full**

Read the entire file before touching it. Key locations to understand:
- Lines ~62–138: state collection and `TranscriptPanel` call
- Lines ~236–247: existing `LaunchedEffect(currentIdx)` and `LaunchedEffect(snapHoverIdx)` — new auto-scroll effect goes here
- Lines ~389–555: `chapterList` composable with `itemsIndexed(chapters)` — sentence rows go inside this block, after the `HorizontalDivider()` at line ~538 and before the deep dive row check at line ~539

- [ ] **Step 2: Remove the `TranscriptPanel` call block**

In `PlayerScreen.kt`, delete these lines (around lines 130–138):

```kotlin
    if (showTranscript) {
        TranscriptPanel(
            segments = transcriptSegments,
            activeIndex = activeSegmentIndex,
            loading = transcriptLoading,
            onSeek = { segment -> vm.seekToSegment(segment) },
            onDismiss = { vm.toggleTranscript() }
        )
    }
```

- [ ] **Step 3: Add the auto-scroll `LaunchedEffect`**

Directly after the existing `LaunchedEffect(snapHoverIdx)` block (around line 244), add:

```kotlin
        LaunchedEffect(activeSegmentIndex) {
            if (showTranscript && activeSegmentIndex >= 0 && transcriptSegments.isNotEmpty()) {
                val activeSeg = transcriptSegments.getOrNull(activeSegmentIndex) ?: return@LaunchedEffect
                val chapterIdx = chapters.indexOfLast { it.startTimeMs / 1000f <= activeSeg.startTimeSec }
                if (chapterIdx >= 0) chapterListState.animateScrollToItem(chapterIdx)
            }
        }
```

- [ ] **Step 4: Add sentence rows inside `itemsIndexed(chapters)`**

Inside the `itemsIndexed(chapters) { idx, chapter ->` lambda, after the `HorizontalDivider()` that follows the chapter `Box` (around line 538) and before the deep dive state check (`if (deepDiveState is DeepDiveState.Playing...`), add:

```kotlin
                    if (showTranscript) {
                        if (idx == 0 && transcriptLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        } else {
                            val chapterStartSec = chapter.startTimeMs / 1000f
                            val nextChapterStartSec =
                                chapters.getOrNull(idx + 1)?.startTimeMs?.div(1000f) ?: Float.MAX_VALUE
                            val chapterSegs = segmentsForChapter(
                                transcriptSegments, chapterStartSec, nextChapterStartSec
                            )
                            chapterSegs.forEachIndexed { localIdx, segment ->
                                val globalIdx = transcriptSegments.indexOf(segment)
                                val isActive = globalIdx == activeSegmentIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { vm.seekToSegment(segment) }
                                        .background(
                                            color = if (isActive) MaterialTheme.colorScheme.primaryContainer
                                                    else Color.Transparent
                                        )
                                        .padding(
                                            start = 64.dp, end = 12.dp,
                                            top = 6.dp, bottom = 6.dp
                                        )
                                ) {
                                    Text(
                                        text = segment.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (localIdx < chapterSegs.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 64.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        }
                    }
```

- [ ] **Step 5: Delete `TranscriptPanel.kt`**

```bash
rm app/src/main/kotlin/com/frybynite/podlore/ui/player/TranscriptPanel.kt
```

- [ ] **Step 6: Remove unused `TranscriptPanel` import from `PlayerScreen.kt`**

If the compiler reports an unused import for `TranscriptPanel`, remove it. (It was likely not imported directly since it's in the same package, but verify the build.)

- [ ] **Step 7: Build to confirm no errors**

```
./gradlew :app:assembleDebug 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`. If `CircularProgressIndicator(modifier = Modifier.size(24.dp))` causes an import error, add `import androidx.compose.foundation.layout.size` — but check the existing imports first since `size` is likely already imported via `Modifier.size` usage elsewhere.

- [ ] **Step 8: Run all tests**

```
./gradlew :app:testDebugUnitTest 2>&1 | tail -15
```

Expected: same pass/fail count as before this task (18 pre-existing failures, no new ones).

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podlore/ui/player/PlayerScreen.kt
git rm app/src/main/kotlin/com/frybynite/podlore/ui/player/TranscriptPanel.kt
git commit -m "feat: render transcript sentences inline in chapter list, remove TranscriptPanel bottom sheet"
```

---

## Self-Review

### Spec coverage

| Requirement | Task |
|-------------|------|
| Chapter rows unchanged (existing highlight logic) | Task 2 — sentence rows are additive, no chapter row code touched |
| Sentences below each chapter when transcript on | Task 2 — `if (showTranscript)` block inside `itemsIndexed` |
| Active sentence gets `primaryContainer` bg | Task 2 — `if (isActive) MaterialTheme.colorScheme.primaryContainer` |
| Inactive sentence transparent bg | Task 2 — `Color.Transparent` |
| 52dp left indent (aligns with chapter title) | Task 2 — `padding(start = 64.dp)` (52dp timestamp col + 12dp padding) |
| Tap sentence → seek | Task 2 — `.clickable { vm.seekToSegment(segment) }` |
| Auto-scroll to chapter containing active sentence | Task 2 — `LaunchedEffect(activeSegmentIndex)` |
| Loading spinner while fetching | Task 2 — `if (idx == 0 && transcriptLoading)` |
| `TranscriptPanel.kt` deleted | Task 2 — `git rm` step |
| `segmentsForChapter` is unit-tested | Task 1 — 6 tests |
| No ViewModel changes | ✅ — no ViewModel files in file map |

### Placeholder scan

No TBD/TODO placeholders. All steps contain actual code.

### Type consistency

- `segmentsForChapter(segments: List<TranscriptSegment>, chapterStartSec: Float, nextChapterStartSec: Float)` — used in Task 2 with `Float` values derived from `chapter.startTimeMs / 1000f` and `chapters.getOrNull(idx + 1)?.startTimeMs?.div(1000f) ?: Float.MAX_VALUE`. Types match.
- `transcriptSegments.indexOf(segment)` — `TranscriptSegment` is a data class; `indexOf` uses `.equals()`. Since `startTimeSec` values are unique per segment, no false matches.
- `activeSegmentIndex: Int` from ViewModel — compared to `globalIdx: Int` from `indexOf`. Both `Int`. ✅
