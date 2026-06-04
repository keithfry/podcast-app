# Backlog

## Voice / NLP

- **Semantic episode search via voice** — commands like "play any stories about Claude or Anthropic" that query episode titles/descriptions using keyword or embedding-based search, then auto-play the best match. Requires: episode description indexing in Room, a search ranking function, and new `VoiceCommand.SEARCH` handler in `VoiceCommandHandler`.

- **"More about this" deep-dive** — voice command ("more about this", "tell me more", etc.) mid-playback. If current chapter has a `url`, fetches and summarizes that URL on-device, generates TTS audio, and injects it between segments. Skipping discards the audio and resumes normal playback. See plan: `docs/plans/2026-06-03-more-about-this-design.md`. On-device AI preference: MediaPipe LLM Inference (Gemma) for summarization, Android TextToSpeech for audio.

## Chapter Context Menu

- **Long-press chapter context menu** — long-pressing a chapter row in `PlayerScreen` opens a dropdown/dialog with: "Open link", "Share link", and "More about this". "Open link" and "Share link" mirror the existing voice command handlers. "More about this" calls `vm.moreAboutThis()` (already implemented). Menu items requiring a URL (`url != null`) should be disabled/hidden when the chapter has no link. Implementation: replace `clickable` on the chapter `Row` with `combinedClickable`, show a `DropdownMenu` anchored to the row on long press.

## Haptics

- **Haptics preference** — expose a setting to disable haptic feedback on snap mode activation in `ChapterProgressBar`. (Noted in progress bar design doc `docs/plans/2026-06-03-progress-bar-design.md`.)

## External Audio Controls

- **Gemini / assistant audio-triggered media controls** — allow Google Assistant / Gemini voice commands ("next", "skip", "back", "pause", etc.) issued outside the app to control playback. Requires registering a `MediaSessionCompat` or `MediaBrowserServiceCompat` that the Android media button framework can route assistant commands to, so Gemini's audio-triggered intents reach the existing `PlaybackService` without the app being foregrounded.

## Chapter list UX

- **"▶ More about this" play indicator on cached chapters** — when a deep dive file is already cached for a chapter, show a small "(▶) More about this" line beneath the chapter title in the list, so the user can see at a glance that the deep dive is ready to play instantly (no loading overlay). Needs design discussion: exact phrasing/icon, whether it appears always or only when the deep dive is the most recent, and interaction (tap the sub-line to trigger, or just visual hint). Relates to `CacheStorage` + `DeepDiveDao` added in caching work.

## TTS

- **Review Miso TTS as alternative** — evaluate Miso TTS for the "More about this" deep-dive audio generation, vs current Modal/Kokoro + Android TextToSpeech fallback. Compare voice quality, latency, cost, and on-device vs cloud tradeoffs.

## Phase 2

- **Always-on wake word** — Picovoice Porcupine or Vosk integration for hands-free activation without tapping the mic FAB.
