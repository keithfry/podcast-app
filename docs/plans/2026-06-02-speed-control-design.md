# Playback Speed Control — Design

## Goal
Global playback speed control in the player: 0.5×–2.0× in 0.1 increments, persisted across sessions.

## UI
- "1.0×" TextButton in PlayerScreen controls row (between +30s and ⏭)
- Tap opens ModalBottomSheet with Slider + current value label
- Sheet dismisses on drag-down or outside tap

## Persistence
- `SpeedPreferences` wraps SharedPreferences, single key `playback_speed`, default `1.0f`
- Hilt-provided via `PreferencesModule`

## ExoPlayer wiring
- `controller.setPlaybackParameters(PlaybackParameters(speed))`
- Applied on slider change and on `connect()` to restore speed for each session

## ViewModel changes
- `playbackSpeed: StateFlow<Float>` initialized from SharedPreferences
- `setSpeed(Float)` — rounds to 1dp, persists, applies to controller

## Files
| File | Change |
|---|---|
| `data/preferences/SpeedPreferences.kt` | New |
| `data/di/PreferencesModule.kt` | New |
| `ui/player/PlayerViewModel.kt` | Add speed state + setSpeed() + apply in connect() |
| `ui/player/PlayerScreen.kt` | Speed button + ModalBottomSheet |
