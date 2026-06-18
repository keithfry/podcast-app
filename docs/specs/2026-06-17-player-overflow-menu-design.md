# Player Screen Overflow Menu Design

## Goal

Reduce icon clutter in the player screen TopAppBar when transcript, sleep, and Chromecast are all available. Move sleep and transcript into a three-dots overflow menu; leave Chromecast at top level.

## Trigger Condition

Overflow is only introduced when `hasTranscript` is true (all three icon categories are present). When `hasTranscript` is false, the TopAppBar actions remain unchanged: Cast · Sleep.

## TopAppBar Actions

| Condition | Actions (left → right) |
|---|---|
| `!hasTranscript` | `MediaRouteButton` (Cast) · Sleep `IconButton` |
| `hasTranscript` | `MediaRouteButton` (Cast) · Three-dots `IconButton` |

## Overflow Menu

- **Anchor:** `IconButton` using `Icons.Filled.MoreVert`, toggles `showMenu: Boolean` local state.
- **Component:** `DropdownMenu` anchored to the three-dots button, dismissed on any item tap or outside tap.
- **Items (in order):**
  1. **Transcript** — leading icon `Icons.Filled.Article`, label "Transcript". Calls `vm.toggleTranscript()`. Dismisses menu.
  2. **Sleep** — leading icon `Icons.Filled.Bedtime`, label "Sleep" normally, "Sleep · MM:SS" when `sleepTimerSeconds != null`. Calls `showSleepSheet = true`. Dismisses menu.

## Unchanged Behavior

- Sleep bottom sheet (`SleepTimerBottomSheet`) — no changes.
- Transcript toggle logic (`vm.toggleTranscript()`) — no changes.
- Cast / `MediaRouteButton` — no changes.
- Automotive guard (`!isAutomotive`) on Cast — no changes.
