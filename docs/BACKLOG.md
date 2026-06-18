# Backlog

## Playback Position / Episode State

- **Return to player screen on app resume** — minimizing and returning to the app navigates back to the main episode list instead of staying on `PlayerScreen`. When the app process is still alive, the back stack should restore `PlayerScreen` for the episode that was playing. Only navigate away if the app process was killed (no saved state). Fix: ensure `PlayerScreen` is in the back stack and `NavController` state survives `onPause`/`onResume` correctly; check `Activity.onNewIntent` and `NavHostFragment` save/restore behavior.

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

- **Transcript: no-chapter episode support** — episodes with a transcript URL but no `chaptersUrl` show the transcript toggle button but render nothing (the `chapters.forEachIndexed` loop doesn't execute). Fix: when `chapters` is empty and `showTranscript` is true, render all transcript segments directly in the `LazyColumn` without chapter grouping.

## Player Screen UX

- ~~**Overflow menu for player screen icons** — when transcript, sleep timer, and Chromecast icons are all visible on the player screen, move sleep and transcript into a three-dots overflow menu, leaving only the Chromecast icon at top level. When Chromecast is unavailable, evaluate whether sleep/transcript still need the overflow or can remain top-level.~~

## Episode List UX

- **Play/pause icon circle styling** — play and pause icons on the episode list row each have a circle border styled to convey download state:
  - **Not downloaded:** play icon with a dashed circle border (primary color).
  - **Downloaded / ready:** play icon with a solid circle border (primary color).
  - **Playing (pause shown):** pause icon with a solid circle border (primary color).
  - Circle size matches the existing `CircularProgressIndicator` shown while downloading.

- **Play icon as default episode action** — replace the current default icon on the right of each episode row with a play icon. Behavior:
  - Tap play on a downloaded episode → play immediately.
  - Tap play on an undownloaded episode → download then play automatically when complete.
  - If a second play-download is triggered before the first finishes, cancel the first download (or deprioritize it) and treat the newly tapped episode as the one to play when its download completes.
  - Visual states for the icon:
    - **Not downloaded:** play icon with a dashed circle border.
    - **Downloading (in progress):** dashed circle animates into a filling progress arc around the play icon.
    - **Downloaded / ready:** play icon with a solid circle border.
    - **Playing (pause shown):** pause icon with a solid circle border.

## Podcast Discovery

- **Podcast search via iTunes and Podcast Index** — search for new podcasts by keyword from within the app. Query both iTunes Search API (`itunes.apple.com/search?media=podcast&term=...`, no auth required) and Podcast Index API (requires free API key) in parallel, merge and deduplicate results by feed URL, and display them in a search UI. Tapping a result should subscribe the user to that podcast (save feed URL to Room and trigger an initial episode fetch).

## Phase 2

- **Always-on wake word** — Picovoice Porcupine or Vosk integration for hands-free activation without tapping the mic FAB.
