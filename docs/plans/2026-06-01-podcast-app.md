# Podcast App Implementation Plan

**Goal:** Build a native Android podcast player with chapter-aware playback, per-chapter link actions, voice control, and an Android Auto foundation.

**Architecture:** MVVM with Repository pattern. `PlaybackService` extends `MediaLibraryService` (Media3) and runs as a foreground service holding the single `ExoPlayer` instance. UI connects via `MediaController`. Room caches subscriptions, episodes, and chapters locally.

**Tech Stack:** Kotlin · Jetpack Compose · Media3/ExoPlayer · Room · Hilt · WorkManager · OkHttp · Moshi · XmlPullParser (RSS) · Min API 29

---

## Completed

- Task 0: Git init + project scaffold
- Task 1: Domain models
- Task 2: RSS parser
- Task 3: Chapters JSON parser
- Task 4: Room database
- Task 5: Network + Repositories
- Task 6: PlaybackService
- Task 7: ViewModels
- Task 8: UI — PodcastListScreen
- Task 9: UI — EpisodeListScreen
- Task 10: UI — PlayerScreen
- Task 11: Navigation graph (tab bar + mini-player overlay, 2026-06-19)
- Task 12: Voice — SpeechRecognizer (Tier 2)
- Task 13: Downloads via WorkManager

---

## Task 14: Final integration test

**Step 1: Run all unit tests**

```bash
./gradlew :app:test
```

Expected: all pass.

**Step 2: Install on device and run manual verification checklist**

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] Open app → Podcast List is empty
- [ ] Tap + → enter `https://keithfry.github.io/web-pages/techradar/AI/podcast.xml` → tap Add
- [ ] Podcast appears in list with title "AI & Robotics Daily Radar"
- [ ] Tap podcast → episode list shows dated episodes
- [ ] Tap episode → player screen opens
- [ ] Chapter list appears (30+ chapters for June 1 episode)
- [ ] Tap Play → audio begins streaming
- [ ] Tap ▶| → jumps to next chapter
- [ ] Tap |◀ → jumps back to previous chapter start
- [ ] Tap 30▶ → seeks forward 30 seconds
- [ ] "Open" button visible on chapters with URLs → tap → Chrome Custom Tab opens
- [ ] "Share" button → share sheet appears
- [ ] Minimize app → audio continues in notification
- [ ] Tap mic → system speech input appears → say "next section" → chapter advances
- [ ] "Ok Google, skip forward" → audio skips

**Step 3: Tag release**

```bash
git tag v0.1.0-alpha
```

---

## Phase 2 Backlog

- Android Auto: implement `MediaLibrarySession.Callback.onGetChildren` browse tree + `CommandButton`s
- Always-on wake word: Picovoice Porcupine integration
- Pull-to-refresh on episode list
- Playback speed control
- Sleep timer
- Discover tab feature
