# Content Caching Design

Date: 2026-06-04

## Goal

Persist generated and downloaded content so it is not re-fetched or re-generated on every use:

1. **Podcast definition** — cached in Room, but refreshed from the feed every time the podcast is opened.
2. **Episode audio** — every downloaded audio file kept on disk (already happens; restructured into a hierarchy).
3. **"More about this" deep dives** — every generated TTS segment kept on disk and reused, keyed by episode + chapter URL. Currently regenerated every time and deleted after playback.
4. **Episode metadata JSON** — the per-episode `.json` sidecar (chapter `link`+`summary` data) cached on disk and reused, instead of re-fetched over the network on every deep-dive miss. Cache-once (fetch on first need, reuse until uninstall).

## Storage layout

All cached files live under internal `filesDir` (private to the app, automatically removed on uninstall, not OS-evictable like `cacheDir`).

```
filesDir/podcasts/
  ai-robotics-daily-a3f91c2e/                 <- slug(podcast.title)-hash8(feedUrl)
    the-ai-stack-battlefield-c81e0d77/        <- slug(episode.title)-hash8(audioUrl)
      audio.mp3                               <- main downloaded episode
      metadata.json                           <- cached .json sidecar (chapter link+summary)
      more-courts-ai-lawsuits-4b2a9f10.wav    <- more-slug(chapter)-hash8(chapterUrl).wav
      more-eu-watchdog-rules-9d7c1a55.wav
```

Naming rules:

- **slug** — lowercase the title, replace each run of non-alphanumeric characters with a single `-`, trim leading/trailing `-`, cap at ~40 characters. If the result is empty (e.g. non-Latin title), use `untitled`.
- **hash8** — first 8 hex characters of the SHA-256 of the source URL (feedUrl / audioUrl / chapterUrl). Guarantees a unique, filesystem-safe directory/file even when two titles slug to the same value.
- The main episode audio is always named `audio.<ext>` where `<ext>` is derived from the audio URL (default `mp3`).
- Each deep dive is `more-<chapterSlug>-<hash8(chapterUrl)>.wav`.

## Components

### CacheStorage (new)

Pure path computation, injected with `@ApplicationContext Context`. No I/O beyond `mkdirs`.

- `podcastDir(feedUrl, podcastTitle): File`
- `episodeDir(feedUrl, podcastTitle, audioUrl, episodeTitle): File`
- `mainAudioFile(... , audioUrl): File` — `episodeDir/audio.<ext>`
- `metadataFile(...): File` — `episodeDir/metadata.json`
- `deepDiveFile(... , chapterUrl, chapterTitle): File` — `episodeDir/more-<slug>-<hash8>.wav`
- private helpers: `slug(String): String`, `hash8(String): String`

Kept small and side-effect-light so it is unit-testable without a device.

### DeepDiveEntity + DeepDiveDao (new)

```kotlin
@Entity(tableName = "deep_dives", primaryKeys = ["episodeAudioUrl", "chapterUrl"])
data class DeepDiveEntity(
    val episodeAudioUrl: String,
    val chapterUrl: String,
    val filePath: String,
    val summaryText: String,
    val createdAt: Long
)
```

DAO:

- `suspend fun get(episodeAudioUrl: String, chapterUrl: String): DeepDiveEntity?`
- `suspend fun upsert(entity: DeepDiveEntity)`
- `suspend fun deleteForEpisode(episodeAudioUrl: String)` (for future cleanup)

Room database version bumped; the migration only **adds** the `deep_dives` table, so it is additive and safe.

### DownloadWorker (changed)

- Inject `podcastDao` (in addition to `episodeDao`) to resolve the podcast title for the slug.
- Write the download to `CacheStorage.mainAudioFile(...)` instead of `filesDir/<hashCode>.mp3`.
- Continue storing the absolute path in `EpisodeEntity.downloadPath`.

### DeepDiveOrchestrator (changed)

`process(...)` gains the context needed to build the cache path (podcast title, episode title, chapter title) and the `DeepDiveDao` + `CacheStorage` dependencies.

New flow:

1. Look up `DeepDiveDao.get(episodeAudioUrl, chapterUrl)`. If a row exists **and** its file exists, return that file immediately — skip fetch / summarize / TTS entirely.
2. On miss: fetch → summarize → synthesize to a temp file (TTS still writes to `cacheDir`), then move/rename the temp file into the episode dir at the `CacheStorage.deepDiveFile(...)` path (same filesystem → atomic rename).
3. Insert a `DeepDiveEntity` row with the final path and the summary text.

`fetchExistingSummary` is changed to read the cached `metadata.json` instead of always hitting the network: if `CacheStorage.metadataFile(...)` exists, parse it; otherwise fetch the `.json` sidecar once, write it to that path, then parse. The episode title / podcast title needed for the path are passed into `process(...)`.

### TtsSynthesizer (unchanged interface)

Still produces a temp file in `cacheDir`; the orchestrator owns moving it to the final cached location. Keeps synthesizer implementations simple and storage policy in one place.

### PlayerViewModel (changed)

- Remove `pendingTtsFile?.delete()` from `skipDeepDive`, `jumpToChapter`, and the `onMediaItemTransition` end block — the TTS file is now the cache and must persist.
- Pass episode title + chapter title into `moreAboutThis(...)` → orchestrator so the cache path can be named.

### Refresh-on-open (changed)

When the episode list for a podcast opens, call `refreshPodcast(feedUrl)` (already implemented as re-parse + upsert). The definition is always current; cached audio and deep-dive files are untouched by a refresh.

## Data flow summary

- **Open podcast** → `refreshPodcast` re-parses feed and upserts Room. Files untouched.
- **Download episode** → audio lands in the episode dir; `downloadPath` updated.
- **"More about this"** → cache hit injects instantly; miss generates, persists the file, and writes a `DeepDiveEntity`.

## Migration / backward compatibility

Existing flat downloads at `filesDir/<hashCode>.mp3` keep working because `downloadPath` stores an absolute path that is honored as-is. New downloads use the new hierarchy. No forced migration of old files.

## Error handling

- Cache lookup with a missing file (row present, file deleted) → treat as a miss and regenerate.
- `mkdirs` failure or rename failure during deep-dive persist → fall back to playing the temp file for this session without inserting a row (no crash; will regenerate next time).
- A `refreshPodcast` network failure on open → keep showing the cached definition; surface a non-fatal error.

## Testing

- `CacheStorage`: slug normalization (case, punctuation, empty/non-Latin, length cap) and `hash8` determinism; two different URLs with the same title produce different dirs.
- `DeepDiveDao`: insert then lookup by composite key (in-memory Room).
- `DeepDiveOrchestrator`: on cache hit, verify fetch/summarize/TTS are **not** called (mock dependencies); on miss, verify a row is written with the final path.
- `metadata.json` caching: first call writes the file and parses it; second call reads from disk without a network fetch.
- Room migration test: opening an old-version DB and migrating creates the `deep_dives` table without data loss.

## Out of scope (future)

- Cache size limits / LRU eviction.
- A "clear cache" or per-podcast delete UI.
- Migrating existing flat downloads into the new hierarchy.
