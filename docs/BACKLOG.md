# Backlog

## Voice / NLP

- **Semantic episode search via voice** — commands like "play any stories about Claude or Anthropic" that query episode titles/descriptions using keyword or embedding-based search, then auto-play the best match. Requires: episode description indexing in Room, a search ranking function, and new `VoiceCommand.SEARCH` handler in `VoiceCommandHandler`.

## Haptics

- **Haptics preference** — expose a setting to disable haptic feedback on snap mode activation in `ChapterProgressBar`. (Noted in progress bar design doc `docs/plans/2026-06-03-progress-bar-design.md`.)

## Rotation / Landscape

- **Survive rotation without restarting audio** — `MainActivity` currently recreates on rotation, releasing and reconnecting `MediaController`. Fix: add `android:configChanges="orientation|screenSize|screenLayout"` to `<activity>` in `AndroidManifest.xml` so rotation is handled without Activity recreation. Or set `configChanges` and handle layout changes manually.

- **Landscape layout for PlayerScreen** — in landscape orientation, split screen 50/50: left half has podcast artwork (smaller) + playback controls + progress bar; right half has chapter list. Use `WindowSizeClass` or `LocalConfiguration.current.orientation` to switch layouts.

## Phase 2

- **Always-on wake word** — Picovoice Porcupine or Vosk integration for hands-free activation without tapping the mic FAB.
