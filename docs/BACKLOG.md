# Backlog

## Playback Position / Episode State

- **Episode switching state inconsistencies** — when navigating between two episodes that both have saved positions, residual state from the previous episode can bleed into the new one before `loadMetadata`'s async DB fetch completes. Symptoms: wrong position shown briefly, wrong chapter highlighted, or play/pause state reflecting the previous episode. Root cause is the window between the synchronous reset in `loadMetadata` and the coroutine completing. Potential fix: pass the target `audioUrl` through the listener guards so any controller event whose `currentMediaItem.mediaId` doesn't match the in-flight load is ignored entirely.

- **Handle non-MP3 mimeTypes for Cast** — when switching to `CastPlayer`, mimeType is hardcoded to `audio/mpeg`. Detect from URL extension (`.aac`, `.opus`, `.ogg`) or stored episode metadata to set the correct mimeType and avoid playback failures on non-MP3 feeds.

- **Cast session resume** — `onSessionResumed` in `PlaybackService` is a no-op; resuming a Cast session after app restart does not restore the current episode or position.

- **Listened / archived episodes** — mark an episode as listened (auto or manual), hide it from the list, and delete its audio file, deep-dive cache, and chapters. DB: add `isListened: Boolean` + `archivedAt: Long?` to `EpisodeEntity` (DB v3→v4 migration). Auto-trigger when playback position reaches ~95% of duration.

## Voice / NLP

- **Semantic episode search via voice** — commands like "play any stories about Claude or Anthropic" that query episode titles/descriptions using keyword or embedding-based search, then auto-play the best match. Requires: episode description indexing in Room, a search ranking function, and new `VoiceCommand.SEARCH` handler in `VoiceCommandHandler`.

- **"More about this" deep-dive** — voice command ("more about this", "tell me more", etc.) mid-playback. If current chapter has a `url`, fetches and summarizes that URL on-device, generates TTS audio, and injects it between segments. Skipping discards the audio and resumes normal playback. See plan: `docs/plans/2026-06-03-more-about-this-design.md`. On-device AI preference: MediaPipe LLM Inference (Gemma) for summarization, Android TextToSpeech for audio.

## Episode Segment Slide-Up Panel

- **Hide link/share/more options when segment has no URL** — when a chapter/segment lacks a `url`, the slide-up panel should not render the link, share, or "more" actions since those are URL-dependent. Conditionally show that section only when `chapter.url != null`.

## Haptics

- **Haptics preference** — expose a setting to disable haptic feedback on snap mode activation in `ChapterProgressBar`. (Noted in progress bar design doc `docs/plans/2026-06-03-progress-bar-design.md`.)

## Android Auto

- **Episode-level artwork in Android Auto** — show each episode's artwork (from RSS `<itunes:image>` or `<image>`) in the Android Auto browse/playback UI. Requires loading the artwork URI into `MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI` (or `METADATA_KEY_DISPLAY_ICON_URI`) on the `MediaSessionCompat` metadata, and setting it on `MediaDescriptionCompat` for browse items returned by `MediaBrowserServiceCompat.onLoadChildren`. Artwork must be a content:// or https:// URI accessible to the Auto host process; bitmap caching via Glide/Coil recommended to avoid blocking the binder thread.

## External Audio Controls

- **Gemini / assistant audio-triggered media controls** — allow Google Assistant / Gemini voice commands ("next", "skip", "back", "pause", etc.) issued outside the app to control playback. Requires registering a `MediaSessionCompat` or `MediaBrowserServiceCompat` that the Android media button framework can route assistant commands to, so Gemini's audio-triggered intents reach the existing `PlaybackService` without the app being foregrounded.

## TTS

- **Review Miso TTS as alternative** — evaluate Miso TTS for the "More about this" deep-dive audio generation, vs current Modal/Kokoro + Android TextToSpeech fallback. Compare voice quality, latency, cost, and on-device vs cloud tradeoffs.

## Read-Along / Transcript

- **Synchronized read-along transcript** — display word-level transcript of the podcast MP3 synchronized to playback position, with the current word/phrase highlighted (karaoke-style). Tap any word to seek to that timestamp.

  **How it works:**
  1. **Transcription:** Call Groq's Whisper API (`whisper-large-v3-turbo`, ~$0.004/min) with `response_format=verbose_json` and `timestamp_granularities[]=word` to get per-word start/end timestamps. Store result as JSON in the episode cache dir alongside existing deep-dive files.
  2. **Data model:** `TranscriptWord(text: String, startMs: Long, endMs: Long)` stored in Room or as a flat JSON file. Chapter boundaries from the existing chapter list can segment the transcript into sections.
  3. **Playback sync:** `PlayerViewModel` exposes current position via `player.currentPosition` polled on a coroutine (16ms tick or `LaunchedEffect` loop). UI maps position → active word index via binary search on `startMs`.
  4. **UI:** `LazyColumn` of word spans (or `FlowRow` for word-wrap). Active word highlighted in accent color, auto-scrolled to stay visible. Tapping a word calls `player.seekTo(word.startMs)`.
  5. **Trigger:** On-demand — "Read along" button in `PlayerScreen` or chapter context menu. Transcript fetched/transcribed on first open, cached thereafter.

  **Implementation touch points:** `GroqTranscriber` (new, calls Whisper API), `TranscriptRepository` (cache JSON to disk, load on demand), `PlayerViewModel.transcriptState: StateFlow<TranscriptUiState>`, `ReadAlongSheet` (new bottom sheet composable), `DeepDiveModule` wiring. Reuses existing `OkHttpClient` and episode cache path helpers.

  **Alternative — on-device:** MediaPipe Audio Classifier or `android.speech.SpeechRecognizer` with `RESULTS_RECOGNITION` extras — no word timestamps on Android without a custom Whisper port. On-device feasible via `whisper.cpp` JNI (150 MB model) but significant integration effort vs. Groq API call.

## Phase 2

- **Always-on wake word** — Picovoice Porcupine or Vosk integration for hands-free activation without tapping the mic FAB.
