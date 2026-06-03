# Chapter Context Menu Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Long-pressing a chapter row opens a context menu with Open link, Share link, and More about this actions.

**Architecture:** Single-file UI change in `PlayerScreen.kt`. Inside the `itemsIndexed` lambda, add `var showMenu` state, swap `clickable` for `combinedClickable`, and anchor a `DropdownMenu` to the row. All three items always render; `enabled = chapter.url != null` greys them out when no URL exists.

**Tech Stack:** Compose `combinedClickable` (foundation), `DropdownMenu` / `DropdownMenuItem` (Material3), existing `CustomTabsIntent` and `ACTION_SEND` patterns already in file.

---

### Task 1: Add imports + swap clickable → combinedClickable + DropdownMenu

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/ui/player/PlayerScreen.kt:10,291-328`

No unit test — Compose UI change. Verify with `assembleDebug`.

**Step 1: Add missing imports**

Replace the existing `import androidx.compose.foundation.clickable` with:

```kotlin
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
```

Also ensure these are present (they already are, but confirm):
- `import androidx.browser.customtabs.CustomTabsIntent`
- `import android.content.Intent`

**Step 2: Add `showMenu` state and wrap Row with Box**

Inside the `itemsIndexed` lambda (after the `val isActive` line, before the `Row`), add:

```kotlin
var showMenu by remember { mutableStateOf(false) }
```

Then wrap the entire `Row { ... }` + `HorizontalDivider()` in a `Box` so the `DropdownMenu` can anchor to it:

```kotlin
Box {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background( ... )
            .then( ... )
            .combinedClickable(
                onClick = { vm.controller?.seekTo(chapter.startTimeMs) },
                onLongClick = { showMenu = true }
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // existing contents unchanged
    }

    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false }
    ) {
        DropdownMenuItem(
            text = { Text("Open link") },
            enabled = chapter.url != null,
            onClick = {
                showMenu = false
                chapter.url?.let { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(it)) }
            }
        )
        DropdownMenuItem(
            text = { Text("Share link") },
            enabled = chapter.url != null,
            onClick = {
                showMenu = false
                chapter.url?.let { url ->
                    context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, url)
                        }, "Share link"
                    ))
                }
            }
        )
        DropdownMenuItem(
            text = { Text("More about this") },
            enabled = chapter.url != null,
            onClick = {
                showMenu = false
                vm.moreAboutThis()
            }
        )
    }

    HorizontalDivider()
}
```

Note: `combinedClickable` requires `@OptIn(ExperimentalFoundationApi::class)` — add that annotation to the `PlayerScreen` composable (or the `chapterList` lambda). The file already has `@OptIn(ExperimentalMaterial3Api::class)`; add `ExperimentalFoundationApi` to it:

```kotlin
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
```

And the import:
```kotlin
import androidx.compose.foundation.ExperimentalFoundationApi
```

**Step 3: Verify build**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

**Step 4: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/ui/player/PlayerScreen.kt
git commit -m "feat: long-press chapter row shows context menu (open, share, more about this)"
```
