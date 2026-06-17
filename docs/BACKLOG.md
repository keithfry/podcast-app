# Backlog

## Playback Position / Episode State

- **Episode switching state inconsistencies** — when navigating between two episodes that both have saved positions, residual state from the previous episode can bleed into the new one before `loadMetadata`'s async DB fetch completes. Symptoms: wrong position shown briefly, wrong chapter highlighted, or play/pause state reflecting the previous episode. Root cause is the window between the synchronous reset in `loadMetadata` and the coroutine completing. Potential fix: pass the target `audioUrl` through the listener guards so any controller event whose `currentMediaItem.mediaId` doesn't match the in-flight load is ignored entirely.

- **Handle non-MP3 mimeTypes for Cast** — when switching to `CastPlayer`, mimeType is hardcoded to `audio/mpeg`. Detect from URL extension (`.aac`, `.opus`, `.ogg`) or stored episode metadata to set the correct mimeType and avoid playback failures on non-MP3 feeds.

- **Cast session resume** — `onSessionResumed` in `PlaybackService` is a no-op; resuming a Cast session after app restart does not restore the current episode or position.

- **Listened / archived episodes** — mark an episode as listened (auto or manual), hide it from the list, and delete its audio file, deep-dive cache, and chapters. DB: add `isListened: Boolean` + `archivedAt: Long?` to `EpisodeEntity` (DB v4→v5 migration). Auto-trigger when playback position reaches ~95% of duration.

## Voice / NLP

- **Semantic episode search via voice** — commands like "play any stories about Claude or Anthropic" that query episode titles/descriptions using keyword or embedding-based search, then auto-play the best match. Requires: episode description indexing in Room, a search ranking function, and new `VoiceCommand.SEARCH` handler in `VoiceCommandHandler`.

- **"More about this" deep-dive** — voice command ("more about this", "tell me more", etc.) mid-playback. If current chapter has a `url`, fetches and summarizes that URL on-device, generates TTS audio, and injects it between segments. Skipping discards the audio and resumes normal playback. See plan: `docs/plans/2026-06-03-more-about-this-design.md`. On-device AI preference: MediaPipe LLM Inference (Gemma) for summarization, Android TextToSpeech for audio.


## Haptics

- **Haptics preference** — expose a setting to disable haptic feedback on snap mode activation in `ChapterProgressBar`. (Noted in progress bar design doc `docs/plans/2026-06-03-progress-bar-design.md`.)

## External Audio Controls

- **Gemini / assistant audio-triggered media controls** — allow Google Assistant / Gemini voice commands ("next", "skip", "back", "pause", etc.) issued outside the app to control playback. Requires registering a `MediaSessionCompat` or `MediaBrowserServiceCompat` that the Android media button framework can route assistant commands to, so Gemini's audio-triggered intents reach the existing `PlaybackService` without the app being foregrounded.

## TTS

- **Review Miso TTS as alternative** — evaluate Miso TTS for the "More about this" deep-dive audio generation, vs current Modal/Kokoro + Android TextToSpeech fallback. Compare voice quality, latency, cost, and on-device vs cloud tradeoffs.

## Read-Along / Transcript

- **Synchronized read-along transcript** — display sentence-level transcript synchronized to playback position, with the active sentence highlighted. Tap any sentence to seek to that timestamp.

  **Transcript file format** (`{prefix}-YYYY-MM-DD.transcript.json`):
  ```json
  {
    "version": "1.0.0",
    "segments": [
      { "startTime": 0.0, "endTime": 3.2, "text": "Welcome to AI Daily Radar." },
      { "startTime": 3.2, "endTime": 7.8, "text": "Today we have twelve stories." }
    ]
  }
  ```
  `startTime` / `endTime` are float seconds from the TTS audio. Full episode coverage (intro + items + outro).

  **How it works:**
  1. **Discovery:** Resolve transcript URL from RSS `<podcast:transcript>` tag (already planned), or derive from episode URL pattern (`{prefix}-YYYY-MM-DD.transcript.json`).
  2. **Fetch:** Lazy — fetch on transcript toggle, not on episode load. Cache JSON to episode cache dir alongside deep-dive files.
  3. **Data model:** `TranscriptSegment(startTime: Float, endTime: Float, text: String)`. No Room storage — flat JSON file cache only.
  4. **Playback sync:** `PlayerViewModel` polls `player.currentPosition` on a coroutine tick. Active segment = first segment where `startTime ≤ currentPositionSec < endTime`. Binary search on `startTime`.
  5. **UI:** Toggle button in `PlayerScreen` shows/hides transcript panel (hidden by default). `LazyColumn` of segment texts. Active segment highlighted in accent color, auto-scrolled into view. Survives scrubbing and chapter jumps. Works offline if JSON is cached.
  6. **Seek:** Tap segment → `player.seekTo((segment.startTime * 1000).toLong())`.

  **Implementation touch points:** `TranscriptRepository` (fetch + cache JSON, discover URL from RSS or pattern), `TranscriptSegment` data class, `PlayerViewModel.transcriptState: StateFlow<TranscriptUiState>`, `ReadAlongPanel` (new composable in `PlayerScreen`). Reuses existing `OkHttpClient` and episode cache path helpers.

## Phase 2

- **Always-on wake word** — Picovoice Porcupine or Vosk integration for hands-free activation without tapping the mic FAB.
