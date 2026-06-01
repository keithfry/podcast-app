# Podcast App — Requirements & Design

## Overview

Native Android podcast app (Kotlin, Jetpack Compose, min API 29) with chapter-aware playback, voice control, per-chapter link actions, and a path to Android Auto.

## Feed Format

RSS 2.0 with iTunes + Podcast Index namespaces. Sample feed: `https://keithfry.github.io/web-pages/techradar/AI/podcast.xml`

Chapters stored as external JSON per episode (Podcast Index JSON Chapters 1.2.0):
```json
{
  "version": "1.2.0",
  "chapters": [
    { "startTime": 20, "endTime": 131, "title": "...", "url": "https://..." }
  ]
}
```
Each chapter has `startTime`, `endTime`, `title`, and optional `url` (link to source article).

## Features

### Phase 1

**Subscriptions**
- Add podcast by RSS feed URL
- List subscribed podcasts
- Refresh feed on demand / on open

**Playback**
- Stream episodes directly from URL
- Download episodes for offline playback (WorkManager + Media3 DownloadManager)
- Background playback via foreground service
- Persistent media notification with controls

**Chapter Navigation**
- Skip ±30 seconds
- Skip to previous / next chapter
- Chapter list panel in player — active chapter highlighted
- Scrubber shows position within episode

**Chapter Link Actions** (per chapter with a `url` field)
- **Open** — Chrome Custom Tab
- **Share** — Android share intent (user picks any app: Todoist, Keep, etc.)

**Voice — Tier 1 (MediaSession, automatic)**
- Google Assistant: play, pause, skip forward/back
- Bluetooth/headset media buttons

**Voice — Tier 2 (in-app mic button)**
- Tap mic → speak command
- Commands: "next section", "previous section", "fast forward", "rewind", "open link", "save link"

### Phase 2

**Android Auto**
- Browse tree: Podcasts → Episodes
- Chapter prev/next as CommandButtons in Auto UI
- Voice commands from the driver's seat

**Voice — Tier 3 (always-on wake word)**
- Picovoice Porcupine or Vosk (on-device, low-power)

## Architecture

**Stack:** Kotlin · Jetpack Compose · Hilt DI · Min API 29

```
UI (Compose screens + ViewModels)
  └── Repositories
        ├── Room DB          — subscriptions, episodes, chapters, download state
        ├── Network          — Retrofit/OkHttp for RSS + chapters JSON
        └── PlaybackService  — MediaLibraryService (ExoPlayer, MediaSession)
```

**Key libraries:**
| Library | Purpose |
|---------|---------|
| `media3-exoplayer` | Core playback |
| `media3-session` | MediaSession, Android Auto foundation |
| `media3-datasource-okhttp` | Streaming |
| `Room` | Local persistence |
| `WorkManager` | Background downloads |
| `Retrofit` + `Moshi` | Network + JSON |
| `ROME` | RSS parsing (handles iTunes + Podcast Index namespaces) |
| `Coil` | Image loading |

## Data Models

```kotlin
Podcast(feedUrl PK, title, author, description, imageUrl?, lastUpdated)

Episode(audioUrl PK, podcastFeedUrl, title, pubDate, durationSeconds,
        chaptersUrl?, downloadPath?, downloadStatus)

Chapter(id PK, episodeAudioUrl, startTimeMs, endTimeMs, title, url?)
```

`DownloadStatus`: `NONE | QUEUED | DOWNLOADING | DONE`

## Screens

| Screen | Content |
|--------|---------|
| **Podcast List** | Subscribed podcasts; FAB to add by URL |
| **Episode List** | Episodes for a podcast; play + download buttons |
| **Player** | Artwork, chapter title, scrubber, ±30s + prev/next chapter controls, chapter list, link action buttons |

## Playback Service

`PlaybackService : MediaLibraryService` — foreground service, single ExoPlayer instance.

Custom `SessionCommand`s: `PREV_CHAPTER`, `NEXT_CHAPTER` — seek to chapter boundary by comparing current position against cached chapter list.

## Voice Commands (Tier 2)

```
"next section" / "skip"       → NEXT_CHAPTER
"previous section" / "back"   → PREV_CHAPTER
"fast forward"                → seek +30s
"rewind"                      → seek -30s
"open link"                   → Chrome Custom Tab
"save link" / "add to list"   → share intent
```

## Verification Checklist

1. Add sample feed URL → episodes load with artwork
2. Tap episode → player opens, chapter list populates
3. Play → audio streams; minimize → continues in background
4. ▶| / |◀ → jumps to next/prev chapter start
5. 30▶ / ◀30 → seeks ±30s
6. Chapter with URL → Open launches Chrome Custom Tab; Share opens share sheet
7. "Ok Google, skip forward" → skips (Tier 1)
8. Mic button → "next section" → chapter advances (Tier 2)
9. Download episode → plays offline
