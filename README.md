# Podcast App

Native Android podcast player with chapter-aware playback, voice control, and per-chapter link actions.

## Features

- **Subscribe** to podcasts by RSS feed URL
- **Stream or download** episodes for offline playback — download progress shown inline per episode
- **Chapter navigation** — skip ±30s, jump between named sections, or tap a chapter to start playback
- **Chapter link actions** — open source article in Chrome Custom Tab or share to any app
- **Episode artwork** — per-episode `itunes:image` with podcast artwork fallback
- **Heard tracking** — auto-mark episodes heard at end; manual toggle; show/hide heard episodes per podcast (persisted)
- **Playback position** — persisted across sessions; marking unheard resets position to zero
- **Deep-dive AI summary** — on-device LiteRt (Gemma) summarizer generates episode summaries
- **Chromecast** — cast to Google Cast devices via dual-player setup with MediaRouteButton
- **Sleep timer** — stop playback after a configurable duration
- **Voice commands** — Google Assistant / headset buttons via MediaSession (Tier 1); in-app mic button with speech recognition (Tier 2)
- **Bluetooth media buttons** — mapped to chapter navigation
- **Background playback** — persistent media notification with controls
- **Android Auto ready** — built on `MediaLibraryService` foundation
- **Podcast list** — sorted alphabetically (case-insensitive)

## Requirements

- Android Studio Hedgehog or later
- JDK 17
- Android device or emulator running API 29+

## Build

```bash
./gradlew assembleDebug
```

Install on connected device:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Test

Run unit tests:

```bash
./gradlew :app:test
```

## Quick Start

1. Launch the app
2. Tap **+** and paste an RSS feed URL, e.g.:
   ```
   https://keithfry.github.io/web-pages/techradar/AI/podcast.xml
   ```
3. Tap a podcast → tap an episode → play

## Architecture

```
UI (Jetpack Compose)
  └── ViewModels (StateFlow)
        └── Repositories
              ├── Room DB        — subscriptions, episodes, chapters, download state
              ├── FeedApi        — OkHttp RSS fetch + Moshi chapters JSON parse
              └── PlaybackService — MediaLibraryService + ExoPlayer (foreground service)
```

**Stack:** Kotlin · Jetpack Compose · Media3/ExoPlayer · Room · Hilt · WorkManager · OkHttp · Moshi · LiteRt (Gemma) · Cast SDK · Min API 29
