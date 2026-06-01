# Podcast App

Native Android podcast player with chapter-aware playback, voice control, and per-chapter link actions.

## Features

- **Subscribe** to podcasts by RSS feed URL
- **Stream or download** episodes for offline playback
- **Chapter navigation** — skip ±30s, or jump between named sections
- **Chapter link actions** — Open source article in Chrome Custom Tab or share to any app
- **Voice commands** — Google Assistant / headset buttons via MediaSession (Tier 1); in-app mic button with speech recognition (Tier 2)
- **Background playback** — persistent media notification with controls
- **Android Auto ready** — built on `MediaLibraryService` foundation

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

**Stack:** Kotlin · Jetpack Compose · Media3/ExoPlayer · Room · Hilt · WorkManager · OkHttp · Moshi · Min API 29
