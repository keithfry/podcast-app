# Chapter Context Menu Design

## Goal

Long-pressing a chapter row opens a context menu with three actions: Open link, Share link, More about this.

## Approach

Replace `clickable` on the chapter `Row` in `PlayerScreen` with `combinedClickable` (tap = seek, long-press = show menu). A `var showMenu by remember { mutableStateOf(false) }` inside the `itemsIndexed` lambda tracks visibility per row. A `DropdownMenu` anchored to the row renders three `DropdownMenuItem`s.

## Menu Items

| Item | Enabled | Action |
|---|---|---|
| Open link | `chapter.url != null` | `CustomTabsIntent` launch |
| Share link | `chapter.url != null` | `ACTION_SEND` intent |
| More about this | `chapter.url != null` | `vm.moreAboutThis()` |

All three items always visible. `enabled = chapter.url != null` handles greying. Disabling "More about this" when no URL avoids the redundant error state from `moreAboutThis()`.

## Scope

- **File changed:** `PlayerScreen.kt` — `chapterList` lambda inside `itemsIndexed`
- **No ViewModel changes** — `moreAboutThis()` already exists
- **No new dependencies** — `DropdownMenu`/`combinedClickable` are in Compose Material3 / Foundation

## Testing

`assembleDebug` + manual on-device test: long-press chapter with URL (all items enabled), long-press chapter without URL (all items greyed out).
