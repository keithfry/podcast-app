# Player Screen Overflow Menu Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the sleep and transcript icons into a three-dots overflow menu when `hasTranscript` is true, leaving the Chromecast button at top level.

**Architecture:** Single-file UI change inside `PlayerScreen.kt`. When `hasTranscript` is false, the TopAppBar actions are unchanged (Cast · Sleep). When `hasTranscript` is true, a `Box` containing a `MoreVert` `IconButton` and an anchored `DropdownMenu` replaces both icons. No ViewModel or data-layer changes needed.

**Tech Stack:** Jetpack Compose `DropdownMenu` / `DropdownMenuItem` (already imported via `androidx.compose.material3.*`), `Icons.Filled.MoreVert` (already available via wildcard icon import).

## Global Constraints

- No new dependencies — all required Compose Material3 components already on the classpath.
- `isAutomotive` guard on the `MediaRouteButton` must remain untouched.
- `formatSleepTimer` is `internal fun` defined at the bottom of `PlayerScreen.kt` — call it directly.

---

### Task 1: Replace TopAppBar actions with conditional overflow

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podlore/ui/player/PlayerScreen.kt:202-231`

**Interfaces:**
- Consumes: `hasTranscript: Boolean`, `sleepTimerSeconds: Int?`, `showSleepSheet: Boolean` (already in scope), `vm.toggleTranscript()`, `formatSleepTimer(Int): String`
- Produces: no new public API

- [ ] **Step 1: Replace the `actions` block**

In `PlayerScreen.kt`, replace lines 202–231 (the entire `actions = { ... }` lambda body, excluding the outer `actions = {` and closing `}`) with the following. The Cast `AndroidView` block is unchanged; only the transcript/sleep section changes.

```kotlin
actions = {
    if (!isAutomotive) {
        AndroidView(
            factory = { ctx ->
                MediaRouteButton(ctx).apply {
                    val selector = MediaRouteSelector.Builder()
                        .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                        .build()
                    routeSelector = selector
                }
            },
            modifier = Modifier.size(48.dp)
        )
    }
    if (hasTranscript) {
        var showOverflow by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { showOverflow = true }) {
                Icon(Icons.Filled.MoreVert, "More options")
            }
            DropdownMenu(
                expanded = showOverflow,
                onDismissRequest = { showOverflow = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Transcript") },
                    leadingIcon = { Icon(Icons.Filled.Article, null) },
                    onClick = {
                        vm.toggleTranscript()
                        showOverflow = false
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (sleepTimerSeconds != null)
                                "Sleep · ${formatSleepTimer(sleepTimerSeconds!!)}"
                            else
                                "Sleep"
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Bedtime, null) },
                    onClick = {
                        showSleepSheet = true
                        showOverflow = false
                    }
                )
            }
        }
    } else {
        IconButton(onClick = { showSleepSheet = true }) {
            if (sleepTimerSeconds != null) {
                Text(
                    formatSleepTimer(sleepTimerSeconds!!),
                    style = MaterialTheme.typography.labelSmall
                )
            } else {
                Icon(Icons.Filled.Bedtime, "Sleep timer")
            }
        }
    }
}
```

- [ ] **Step 2: Build to verify no compile errors**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL` with no errors.

- [ ] **Step 3: Run existing sleep timer format tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.frybynite.podlore.ui.player.SleepTimerFormatTest"
```

Expected: `BUILD SUCCESSFUL`, all 8 tests pass.

- [ ] **Step 4: Manual verification — episode with transcript**

Launch the app on a device/emulator. Open an episode that has a transcript URL. Verify:
1. TopAppBar shows Cast button + three-dots (`⋮`) icon only.
2. Tapping `⋮` opens a dropdown with "Transcript" (article icon) and "Sleep" (bedtime icon).
3. Tapping "Transcript" toggles the transcript panel and dismisses the menu.
4. Tapping "Sleep" opens the sleep timer bottom sheet and dismisses the menu.
5. With a sleep timer active, the Sleep item label reads "Sleep · 4:30" (or similar countdown).

- [ ] **Step 5: Manual verification — episode without transcript**

Open an episode with no transcript. Verify:
1. TopAppBar shows Cast button + Sleep icon only (unchanged from before).
2. Sleep icon/countdown behavior is unchanged.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podlore/ui/player/PlayerScreen.kt
git commit -m "feat: move sleep and transcript into overflow menu when transcript available"
```

- [ ] **Step 7: Mark backlog item done**

In `docs/BACKLOG.md`, mark "Overflow menu for player screen icons" as done (add `~~` strikethrough or move to a Done section per project convention).
