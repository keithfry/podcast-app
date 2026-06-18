# Database Specification

Room database (`PodcastDatabase`), SQLite via Android Room.  
Current version: **6**  
Package: `com.frybynite.podcastapp.data.db`

---

## Tables

### `podcasts`

Primary key: `feedUrl`

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `feedUrl` | TEXT | NOT NULL | PK — RSS feed URL |
| `title` | TEXT | NOT NULL | |
| `author` | TEXT | NOT NULL | |
| `description` | TEXT | NOT NULL | |
| `imageUrl` | TEXT | NULL | Podcast artwork URL |
| `lastUpdated` | INTEGER | NOT NULL | Unix epoch ms |

---

### `episodes`

Primary key: `audioUrl`  
Index: `podcastFeedUrl`

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `audioUrl` | TEXT | NOT NULL | — | PK — MP3/audio URL |
| `podcastFeedUrl` | TEXT | NOT NULL | — | FK → `podcasts.feedUrl` (not enforced) |
| `title` | TEXT | NOT NULL | — | |
| `pubDate` | INTEGER | NOT NULL | — | Unix epoch ms |
| `durationSeconds` | INTEGER | NOT NULL | — | |
| `chaptersUrl` | TEXT | NULL | — | URL for Podcast Index chapters JSON |
| `transcriptUrl` | TEXT | NULL | — | URL for pre-generated transcript JSON (podcast:transcript tag) |
| `imageUrl` | TEXT | NULL | — | Episode artwork URL (`itunes:image`); fallback to podcast artwork |
| `downloadPath` | TEXT | NULL | — | Local file path when downloaded |
| `downloadStatus` | TEXT | NOT NULL | `"NONE"` | Enum: `NONE`, `QUEUED`, `DOWNLOADING`, `DONE` |
| `lastPositionMs` | INTEGER | NOT NULL | `0` | Playback resume position |
| `isHeard` | INTEGER | NOT NULL | `0` | 1 when episode has been listened to |

---

### `chapters`

Primary key: `id` (auto-generated)  
Index: `episodeAudioUrl`

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `id` | INTEGER | NOT NULL | PK autoincrement |
| `episodeAudioUrl` | TEXT | NOT NULL | FK → `episodes.audioUrl` (not enforced) |
| `startTimeMs` | INTEGER | NOT NULL | |
| `endTimeMs` | INTEGER | NOT NULL | |
| `title` | TEXT | NOT NULL | |
| `url` | TEXT | NULL | Associated web URL (used for deep-dive) |

Chapters are always replaced atomically per episode (`replaceChaptersForEpisode` transaction: delete then insert).

---

### `deep_dives`

Composite primary key: `(episodeAudioUrl, chapterUrl)`

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `episodeAudioUrl` | TEXT | NOT NULL | PK part 1 — FK → `episodes.audioUrl` (not enforced) |
| `chapterUrl` | TEXT | NOT NULL | PK part 2 — FK → `chapters.url` (not enforced) |
| `filePath` | TEXT | NOT NULL | Local path to cached audio file |
| `summaryText` | TEXT | NOT NULL | Pre-generated text summary |
| `createdAt` | INTEGER | NOT NULL | Unix epoch ms |

---

## DAOs

| DAO | Table | Key operations |
|-----|-------|----------------|
| `PodcastDao` | `podcasts` | `getAll(): Flow`, `getByUrl()`, `upsert()`, `delete()` |
| `EpisodeDao` | `episodes` | `getForPodcast(): Flow`, `getByAudioUrl()`, `getByAudioUrlFlow(): Flow`, `upsertAll()`, `updateDownloadStatus()`, `updateLastPosition()`, `markHeard()` |
| `ChapterDao` | `chapters` | `getForEpisode(): Flow`, `countForEpisode()`, `replaceChaptersForEpisode()` (transaction) |
| `DeepDiveDao` | `deep_dives` | `get()`, `upsert()`, `flowForEpisode()`, `deleteForEpisode()` |

---

## Version History

| Version | Change |
|---------|--------|
| 1 | Initial schema: `podcasts`, `episodes`, `chapters` |
| 2 | Added `lastPositionMs` to `episodes` |
| 3 | Added `deep_dives` table |
| 4 | Added `imageUrl` to `episodes` (per-episode `itunes:image` artwork) |
| 5 | Added `isHeard` to `episodes`; auto-set at 95% playback or manual mark |
| 6 | Added `transcriptUrl` to `episodes`; URL for pre-generated sentence-level transcript JSON |
