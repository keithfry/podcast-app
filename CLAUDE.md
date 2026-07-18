# Project Instructions

Podlore — native Android podcast player. Kotlin, Jetpack Compose, Hilt DI, Room, Media3/ExoPlayer. Single Gradle module (`:app`).

## Database

When making any change to the Room database schema (entities, DAOs, migrations, or `PodcastDatabase` version), update `docs/specs/database.md` to reflect the change before considering the task complete.

Current: `PodcastDatabase` v7 (`app/src/main/kotlin/com/frybynite/podlore/data/db/PodcastDatabase.kt`). Entities: `PodcastEntity`, `EpisodeEntity`, `ChapterEntity`, `DeepDiveEntity`. DAOs: `PodcastDao`, `EpisodeDao`, `ChapterDao`, `DeepDiveDao`. `exportSchema = false` — no `/schemas` dir, no generated migration JSON.

## Package layout (`app/src/main/kotlin/com/frybynite/podlore/`)

- `data/db` — Room database, entities, DAOs
- `data/network` — RSS/OPML parsing, chapters/transcript API responses
- `data/repository` — `PodcastRepository` (podcast + episode CRUD, refresh, download orchestration, heard/like state, unsubscribe cleanup — no separate `EpisodeRepository`), `ChapterRepository`, `TranscriptRepository`, `SearchRepository` (podcast discovery)
- `data/download` — episode file download (WorkManager)
- `data/storage` — `CacheStorage`, local file layout for downloaded audio/deep-dive files
- `data/preferences` — SharedPreferences wrappers (e.g. `EpisodeListPreferences`, `SpeedPreferences`)
- `data/di` — Hilt modules: `NetworkModule`, `WorkModule`, `DatabaseModule`, `PreferencesModule`
- `domain/model` — plain domain models (`Podcast`, `Episode`, `Chapter`, ...), separate from Room entities
- `deepdive` — "Deep Dive" / "more about this" AI summarization + TTS pipeline (see below)
- `playback` — `PlaybackController` (app-process singleton wrapping a Media3 `MediaController`), `PlaybackModule`
- `service` — `PlaybackService` (`MediaLibraryService`), `ChapterNavigator`, `MediaButtonHandler`
- `cast` — Chromecast integration
- `ui/*` — Compose screens + `@HiltViewModel`s per feature: `discover`, `episodes`, `player`, `podcast`, `podcasts`, `common`, `theme`

## Data flow

Network (RSS) / Room DB → Repository → ViewModel (`StateFlow`) → Compose UI (`collectAsState`). Repositories are the only layer touching DAOs/network; ViewModels never query Room directly except via a repository or DAO passed in for read-only flows (e.g. `PodcastDao.observeByUrl`).

## Playback architecture

- `service/PlaybackService.kt` — `MediaLibraryService`, owns the `MediaLibrarySession`, delegates chapter-skip to `ChapterNavigator` and hardware buttons to `MediaButtonHandler`.
- `playback/PlaybackController.kt` — Hilt-injected singleton, holds the `MediaController` connected to the service; exposes `currentlyPlayingUrl`, `currentTitle`, `isPlaying` as `StateFlow`s that ViewModels (`PlayerViewModel`, `AppViewModel`) observe.
- Flow: UI action → `PlaybackController` → `MediaController` → `PlaybackService`/ExoPlayer → state bubbles back up via the controller's flows.

## Deep Dive (on-device/cloud AI summarization)

Entirely in `deepdive/`:
- `TextSummarizer` interface — `LiteRtTextSummarizer` (on-device LiteRT-LM, currently `litertlm-android:0.13.1` — see pinned-version note below), `AiCoreTextSummarizer`, `GemmaTextSummarizer`, `GroqTextSummarizer` (cloud), `FallbackTextSummarizer`.
- `TtsSynthesizer` interface — `AndroidTtsSynthesizer`, `KokoroTtsSynthesizer`, `GroqTtsSynthesizer`, `FallbackTtsSynthesizer`.
- `DeepDiveRouter` — picks summarizer/TTS backend based on device capability (`OpenClDetector`) and model availability (`ModelDownloadManager`).
- `DeepDiveOrchestrator` — fetches chapter article content (`UrlContentFetcher`), summarizes, synthesizes audio, persists to `DeepDiveEntity` via `DeepDiveDao`, drives download notifications.

**`litertlm-android` is pinned to `0.13.1`, not `latest.release`** (`app/build.gradle.kts`) — 0.14.0 ships bytecode that calls `SendChannel.close$default` as a direct interface static method, which doesn't exist in any released `kotlinx-coroutines-core` (only exists on `SendChannel$DefaultImpls`), causing a guaranteed `NoSuchMethodError` crash on every summarization. Do not bump this dependency without re-verifying the call site in `Conversation.class` links against the actual coroutines ABI.

## Build config

Single module (`:app`, see `settings.gradle.kts`). Key versions (`gradle/libs.versions.toml`): AGP 8.5.0, Kotlin 2.0.0, Hilt 2.56, Compose BOM 2024.06.00, Media3 1.3.1, Room 2.6.1, coroutines 1.8.1 (resolves to 1.9.0 via conflict resolution). `compileSdk 34`, `minSdk 29`, `targetSdk 34`.

## Tests

- Unit tests: `app/src/test/kotlin/...`, mirrors main package structure. Includes Compose snapshot tests (`ChapterProgressBarSnapshotTest`, `EpisodeRowSnapshotTest`) with golden images under `app/src/test/snapshots/images/`.
- Instrumented tests: `app/src/androidTest/kotlin/...` — DAO tests (`PodcastDaoTest`, `EpisodeDaoTest`, `ChapterDaoTest`, `DeepDiveDaoTest`), `CastOptionsProviderTest`, `AddMediaItemsTest`. Uses `room-testing`.
- Known test gaps tracked in `docs/specs/testing-backlog.md` — check there before assuming a code path is untested.

## Docs

- `docs/BACKLOG.md` — future work items.
- `docs/specs/database.md` — Room schema reference, keep in sync per rule above.
- `docs/specs/testing-backlog.md` — known testing gaps.
- `docs/specs/*-design.md` — feature design specs (Android Auto, Chromecast, inline transcript, episode play icon, global playback controller, player overflow menu, discover).
- `docs/plans/*.md` — dated implementation/design/progress plans, one per feature, naming convention `YYYY-MM-DD-<feature>[-design|-progress].md`. Check for an existing plan before starting new feature work — most major features (deep dive, Chromecast, Android Auto, read-along transcript, speed control) already have one.
