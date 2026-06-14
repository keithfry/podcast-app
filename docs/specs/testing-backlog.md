# Testing Backlog

## High Impact

### DAO Tests — in-memory Room (`androidTest`, `@RunWith(AndroidJUnit4::class)`)

- **`EpisodeDaoTest`** — full CRUD, `updateLastPosition`, `getForPodcast` ordered by `pubDate DESC`, upsert conflict replaces existing row, Flow emits on change
- **`ChapterDaoTest`** — `replaceChaptersForEpisode` atomicity (old rows deleted, new rows present); chapters across different episodes don't bleed into each other
- **`DeepDiveDaoTest`** — composite PK upsert overwrites on conflict, `flowForEpisode` emits on insert
- **`PodcastDaoTest`** — `getAll` ordered by `lastUpdated DESC`, upsert-replace semantics, delete removes row

### PlayerViewModel — state machine

- **`loadMetadata`** sets `episodeTitle`, `podcastTitle`, `podcastImageUrl` from DB mocks
- **`setSleepTimer(1)`** — countdown reaches 0, `isPlaying` transitions false (use `TestCoroutineScheduler`)
- **`savePosition()`** — calls `episodeDao.updateLastPosition` with correct `audioUrl` + `positionMs`
- **`skipDeepDive()`** — `deepDiveState` transitions to `Idle`
- **`isCasting`** — starts false; Cast session listener callbacks flip it true then false

### PlaybackService (`androidTest`, `ServiceTestRule`)

- Service binds and creates `MediaLibrarySession`
- `onAddMediaItems` resolves local `downloadPath` URI when `downloadStatus == DOWNLOADED`; falls back to `audioUrl` for non-downloaded episodes
- `switchToPlayer` preserves current position and playing state when switching ExoPlayer → mock CastPlayer
- `onMediaButtonEvent(KEYCODE_MEDIA_NEXT)` delegates to chapter navigator

### ChapterRepository ✓

- ~~`fetchAndCacheChapters` cache hit: MockWebServer receives 0 requests~~ ✓
- ~~`fetchAndCacheChapters` cache miss: fetches, parses, and saves chapters to DAO~~ ✓

---

## Medium Impact

### DeepDiveOrchestrator ✓

- ~~Fresh miss: fetch → summarize → TTS → `deepDiveDao.upsert` all called in sequence~~ ✓
- ~~`fetchExistingSummary` reads `summary` field from metadata JSON correctly~~ ✓
- ~~File relocation with fallback to copy when rename fails~~ ✓

### PlayerScreen Compose UI (`createComposeRule()`)

- ~~Play button shown on initial render~~ ✓ (androidTest)
- ~~Speed label shows 1.0× initially~~ ✓ (androidTest)
- ~~Speed label updates after setSpeed~~ ✓ (androidTest)
- ~~Sleep timer text shown after setSleepTimer~~ ✓ (androidTest)
- `ChapterProgressBar` drag released near chapter boundary snaps to chapter `startTimeMs` (needs real touch input)
- Deep dive "Skip" button behavior when `deepDiveState == Playing` (needs controller mock)

### Voice command integration (end-to-end with `PlayerViewModel`)

- "next chapter" command → `currentChapterIndex` increments
- "more about this" with chapter that has a URL → `deepDiveState` transitions to `Fetching`

### PodcastRepository gaps ✓

- ~~`refreshPodcast(feedUrl)` re-fetches RSS and updates episodes in DB~~ ✓
- ~~`removePodcast(feedUrl)` deletes podcast and cascades episode removal~~ ✓
- ~~Network failure sets error state, existing DB data preserved~~ ✓

### PlayerViewModel — playback

- ~~`setSpeed(1.5f)` — `playbackSpeed` StateFlow emits rounded value~~ ✓
- `jumpToChapter(startTimeMs)` during deep dive stops TTS before seeking (needs private state access)

---

## Low Impact

### Download flow (`WorkManagerTestInitHelper`)

- `DownloadWorker` writes file to `downloadPath` and calls `updateDownloadStatus` → `DOWNLOADED`
- Failure leaves `downloadStatus` as `NONE`

### Cast session lifecycle (requires GMS emulator image)

- `isCasting` flips true when mock Cast session starts
- `onSessionEnded` switches active player back to ExoPlayer

### MediaRouteButton visibility (requires GMS emulator image)

- Button visible in `PlayerScreen` when Cast device available
- Badge shows on `EpisodeListScreen` when actively casting

### CastOptionsProvider ✓

- ~~`getCastOptions(context)` returns non-null options~~ ✓ (androidTest)
- ~~`getAdditionalSessionProviders(context)` returns null~~ ✓ (androidTest)
