# Android Auto Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire up Android Auto media browsing, Google Assistant play-from-search, podcast artwork, and chapter nav custom buttons — all within the existing `PlaybackService`/`MediaLibraryService` stack, no new deps.

**Architecture:** Four surgical changes: (1) new `res/xml/automotive_app_desc.xml` declares the app as an Auto media source; (2) `AndroidManifest.xml` gains two `<meta-data>` entries; (3) `PlaybackService.kt` gains artwork on `MediaItem`s and a `findEpisodeForQuery` helper + extended `onAddMediaItems` for Assistant search; (4) `BACKLOG.md` gets the `onSearch` follow-up. Chapter nav (`setCustomLayout`) already works for all controllers including Auto — no changes needed.

**Tech Stack:** Media3 `MediaLibraryService`, Kotlin coroutines (`serviceScope.future {}`), MockK + JUnit4 for unit tests.

---

## File Map

| File | Action |
|------|--------|
| `app/src/main/res/xml/automotive_app_desc.xml` | **Create** — Auto app descriptor |
| `app/src/main/AndroidManifest.xml` | **Modify** — add two `<meta-data>` entries |
| `app/src/main/kotlin/com/frybynite/podcastapp/service/PlaybackService.kt` | **Modify** — artwork, search helper, `onAddMediaItems` |
| `app/src/test/kotlin/com/frybynite/podcastapp/service/PlaybackServiceSearchTest.kt` | **Create** — unit tests for `findEpisodeForQuery` |
| `docs/BACKLOG.md` | **Modify** — add `onSearch` follow-up |

---

## Task 1: Automotive XML Descriptor

**Files:**
- Create: `app/src/main/res/xml/automotive_app_desc.xml`

- [ ] **Step 1: Create the XML directory if it doesn't exist**

```bash
mkdir -p app/src/main/res/xml
```

- [ ] **Step 2: Create `automotive_app_desc.xml`**

Create `app/src/main/res/xml/automotive_app_desc.xml` with this exact content:

```xml
<?xml version="1.0" encoding="utf-8"?>
<automotiveApp>
    <uses name="media"/>
</automotiveApp>
```

- [ ] **Step 3: Verify file exists**

```bash
cat app/src/main/res/xml/automotive_app_desc.xml
```

Expected output: the XML above.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/xml/automotive_app_desc.xml
git commit -m "feat: add Android Auto app descriptor XML"
```

---

## Task 2: Manifest Changes

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

The manifest needs two additions:
1. An `<application>`-level `<meta-data>` pointing to the descriptor — tells Auto this app is a media source.
2. A `<service>`-level `<meta-data>` for the Auto media icon.

- [ ] **Step 1: Add `<meta-data>` inside the `<application>` block**

In `app/src/main/AndroidManifest.xml`, find the `<application` opening tag. Add this **as the first child** inside `<application>` (before the `<activity>` declaration):

```xml
        <meta-data
            android:name="com.google.android.gms.car.application"
            android:resource="@xml/automotive_app_desc" />
```

- [ ] **Step 2: Add `<meta-data>` inside the `PlaybackService` `<service>` block**

Find `<service android:name=".service.PlaybackService"`. Add this **inside** that `<service>` element, after the existing `<intent-filter>` block:

```xml
            <meta-data
                android:name="com.google.android.gms.car.service.MEDIA_APP_ICON"
                android:resource="@mipmap/ic_launcher" />
```

- [ ] **Step 3: Verify manifest compiles**

```bash
./gradlew :app:assembleDebug --quiet
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat: declare Android Auto media source in manifest"
```

---

## Task 3: Artwork on MediaItems

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/service/PlaybackService.kt`

Auto shows artwork prominently. Source: `Podcast.imageUrl` (already in `PodcastEntity`). Episodes inherit their podcast's image.

Two extension functions at the bottom of `PlaybackService.kt` need updating, plus the `onGetChildren` call site that constructs episode items.

- [ ] **Step 1: Add `Uri` import to `PlaybackService.kt`**

At the top of `PlaybackService.kt`, after the existing imports, add:

```kotlin
import android.net.Uri
```

- [ ] **Step 2: Update `Podcast.toMediaItem()` to include artwork**

Replace the existing `Podcast.toMediaItem()` at the bottom of the file:

```kotlin
// BEFORE
private fun Podcast.toMediaItem() = MediaItem.Builder()
    .setMediaId("${PlaybackService.PODCAST_PREFIX}$feedUrl")
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(author)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST)
            .build()
    ).build()
```

With:

```kotlin
// AFTER
private fun Podcast.toMediaItem() = MediaItem.Builder()
    .setMediaId("${PlaybackService.PODCAST_PREFIX}$feedUrl")
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(author)
            .setArtworkUri(imageUrl?.let { Uri.parse(it) })
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST)
            .build()
    ).build()
```

- [ ] **Step 3: Update `Episode.toMediaItem()` to accept podcast image URL**

Replace the existing `Episode.toMediaItem()` at the bottom of the file:

```kotlin
// BEFORE
private fun Episode.toMediaItem() = MediaItem.Builder()
    .setMediaId(audioUrl)
    .setUri(audioUrl)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .build()
    ).build()
```

With:

```kotlin
// AFTER
private fun Episode.toMediaItem(podcastImageUrl: String?) = MediaItem.Builder()
    .setMediaId(audioUrl)
    .setUri(audioUrl)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtworkUri(podcastImageUrl?.let { Uri.parse(it) })
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .build()
    ).build()
```

- [ ] **Step 4: Update `onGetChildren` to pass artwork to episode items**

In `onGetChildren`, find the `parentId.startsWith(PODCAST_PREFIX)` branch:

```kotlin
// BEFORE
parentId.startsWith(PODCAST_PREFIX) -> {
    val feedUrl = parentId.removePrefix(PODCAST_PREFIX)
    val episodes = podcastRepo.episodesForPodcast(feedUrl).first()
    LibraryResult.ofItemList(episodes.map { it.toMediaItem() }, params)
}
```

Replace with:

```kotlin
// AFTER
parentId.startsWith(PODCAST_PREFIX) -> {
    val feedUrl = parentId.removePrefix(PODCAST_PREFIX)
    val episodes = podcastRepo.episodesForPodcast(feedUrl).first()
    val podcast = podcastRepo.podcasts.first().find { it.feedUrl == feedUrl }
    LibraryResult.ofItemList(episodes.map { it.toMediaItem(podcast?.imageUrl) }, params)
}
```

- [ ] **Step 5: Verify build**

```bash
./gradlew :app:assembleDebug --quiet
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/service/PlaybackService.kt
git commit -m "feat: add podcast artwork to Auto MediaItems"
```

---

## Task 4: Google Assistant Play-From-Search

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/service/PlaybackService.kt`
- Create: `app/src/test/kotlin/com/frybynite/podcastapp/service/PlaybackServiceSearchTest.kt`

When the user says "Hey Google, play [query] on Podcast App", Auto delivers a `MediaItem` with `requestMetadata.searchQuery` set and no URI. `onAddMediaItems` needs to detect this case and resolve it to a real playable item.

The matching logic is extracted to a top-level `internal` function `findEpisodeForQuery` so it can be unit-tested without involving Android framework or Hilt.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/frybynite/podcastapp/service/PlaybackServiceSearchTest.kt`:

```kotlin
package com.frybynite.podcastapp.service

import com.frybynite.podcastapp.domain.model.DownloadStatus
import com.frybynite.podcastapp.domain.model.Episode
import com.frybynite.podcastapp.domain.model.Podcast
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackServiceSearchTest {

    private val podcast1 = Podcast(
        feedUrl = "https://feed1.com",
        title = "Science Vs",
        author = "Spotify",
        description = "",
        imageUrl = "https://img.com/sv.jpg"
    )
    private val podcast2 = Podcast(
        feedUrl = "https://feed2.com",
        title = "Hidden Brain",
        author = "NPR",
        description = "",
        imageUrl = "https://img.com/hb.jpg"
    )
    private val ep1 = Episode(
        audioUrl = "https://audio.com/sv-ep1.mp3",
        podcastFeedUrl = "https://feed1.com",
        title = "Caffeine: How Bad Is It Really?",
        pubDate = 1000L,
        durationSeconds = 3600,
        chaptersUrl = null
    )
    private val ep2 = Episode(
        audioUrl = "https://audio.com/sv-ep2.mp3",
        podcastFeedUrl = "https://feed1.com",
        title = "Ozempic: The Miracle Drug?",
        pubDate = 2000L,
        durationSeconds = 2700,
        chaptersUrl = null
    )
    private val ep3 = Episode(
        audioUrl = "https://audio.com/hb-ep1.mp3",
        podcastFeedUrl = "https://feed2.com",
        title = "The Influence of Habits",
        pubDate = 1500L,
        durationSeconds = 1800,
        chaptersUrl = null
    )

    private val podcasts = listOf(podcast1, podcast2)
    private val episodesByFeed = mapOf(
        "https://feed1.com" to listOf(ep1, ep2),
        "https://feed2.com" to listOf(ep3)
    )

    @Test fun `exact episode title match returns that episode and podcast image`() {
        val result = findEpisodeForQuery("caffeine: how bad is it really?", podcasts, episodesByFeed)
        assertEquals(ep1, result?.first)
        assertEquals("https://img.com/sv.jpg", result?.second)
    }

    @Test fun `partial episode title match returns matching episode`() {
        val result = findEpisodeForQuery("ozempic", podcasts, episodesByFeed)
        assertEquals(ep2, result?.first)
    }

    @Test fun `podcast title match returns first episode of that podcast`() {
        val result = findEpisodeForQuery("hidden brain", podcasts, episodesByFeed)
        assertEquals(ep3, result?.first)
        assertEquals("https://img.com/hb.jpg", result?.second)
    }

    @Test fun `case insensitive match works`() {
        val result = findEpisodeForQuery("SCIENCE VS", podcasts, episodesByFeed)
        assertEquals(ep1, result?.first)
    }

    @Test fun `no match returns null`() {
        val result = findEpisodeForQuery("quantum physics", podcasts, episodesByFeed)
        assertNull(result)
    }

    @Test fun `empty podcast list returns null`() {
        val result = findEpisodeForQuery("anything", emptyList(), emptyMap())
        assertNull(result)
    }

    @Test fun `podcast with no episodes returns null for podcast title match`() {
        val result = findEpisodeForQuery("science vs", podcasts, mapOf("https://feed1.com" to emptyList()))
        assertNull(result)
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.frybynite.podcastapp.service.PlaybackServiceSearchTest" --quiet
```

Expected: FAILED — `findEpisodeForQuery` not defined yet.

- [ ] **Step 3: Add `findEpisodeForQuery` to `PlaybackService.kt`**

Add this function at the bottom of `PlaybackService.kt`, after the existing extension functions:

```kotlin
internal fun findEpisodeForQuery(
    query: String,
    podcasts: List<com.frybynite.podcastapp.domain.model.Podcast>,
    episodesByFeed: Map<String, List<com.frybynite.podcastapp.domain.model.Episode>>
): Pair<com.frybynite.podcastapp.domain.model.Episode, String?>? {
    val q = query.lowercase()
    for (podcast in podcasts) {
        val episodes = episodesByFeed[podcast.feedUrl] ?: emptyList()
        val match = episodes.firstOrNull { it.title.lowercase().contains(q) }
            ?: if (podcast.title.lowercase().contains(q)) episodes.firstOrNull() else null
        if (match != null) return Pair(match, podcast.imageUrl)
    }
    return null
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.frybynite.podcastapp.service.PlaybackServiceSearchTest" --quiet
```

Expected: `BUILD SUCCESSFUL` — 7 tests pass.

- [ ] **Step 5: Add `resolveSearchQuery` to `PlaybackService`**

Add this private suspend function inside the `PlaybackService` class body (after the `chapters` property, before the `callback` object):

```kotlin
private suspend fun resolveSearchQuery(query: String): MediaItem {
    val allPodcasts = podcastRepo.podcasts.first()
    val episodesByFeed = allPodcasts.associate { podcast ->
        podcast.feedUrl to podcastRepo.episodesForPodcast(podcast.feedUrl).first()
    }
    val match = findEpisodeForQuery(query, allPodcasts, episodesByFeed)
    if (match != null) return match.first.toMediaItem(match.second)
    // Fallback: first episode of first podcast
    val fallbackPodcast = allPodcasts.firstOrNull() ?: return MediaItem.EMPTY
    val fallbackEpisode = podcastRepo.episodesForPodcast(fallbackPodcast.feedUrl).first().firstOrNull()
        ?: return MediaItem.EMPTY
    return fallbackEpisode.toMediaItem(fallbackPodcast.imageUrl)
}
```

- [ ] **Step 6: Update `onAddMediaItems` to use `serviceScope.future {}` and handle search queries**

Replace the existing `onAddMediaItems` override inside the `callback` object:

```kotlin
// BEFORE
override fun onAddMediaItems(
    mediaSession: MediaSession,
    controller: MediaSession.ControllerInfo,
    mediaItems: List<MediaItem>
): ListenableFuture<List<MediaItem>> =
    Futures.immediateFuture(mediaItems.map { item ->
        if (item.localConfiguration?.uri != null) item
        else item.buildUpon().setUri(item.mediaId).build()
    })
```

With:

```kotlin
// AFTER
override fun onAddMediaItems(
    mediaSession: MediaSession,
    controller: MediaSession.ControllerInfo,
    mediaItems: List<MediaItem>
): ListenableFuture<List<MediaItem>> =
    serviceScope.future {
        mediaItems.map { item ->
            val query = item.requestMetadata.searchQuery
            when {
                query != null -> resolveSearchQuery(query)
                item.localConfiguration?.uri != null -> item
                else -> item.buildUpon().setUri(item.mediaId).build()
            }
        }
    }
```

- [ ] **Step 7: Verify build**

```bash
./gradlew :app:testDebugUnitTest --quiet
```

Expected: `BUILD SUCCESSFUL` — all tests pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/service/PlaybackService.kt \
        app/src/test/kotlin/com/frybynite/podcastapp/service/PlaybackServiceSearchTest.kt
git commit -m "feat: resolve Google Assistant play-from-search in PlaybackService"
```

---

## Task 5: Backlog Update

**Files:**
- Modify: `docs/BACKLOG.md`

- [ ] **Step 1: Add `onSearch` item to the Voice / NLP section**

In `docs/BACKLOG.md`, find the `## Voice / NLP` section. Add this item after the existing "Semantic episode search via voice" entry:

```markdown
- **`onSearch` / semantic episode search from Assistant** — extend `PlaybackService.Callback.onSearch` to handle Assistant browse queries ("find episodes about AI"). Requires episode description indexing in Room and a ranking function. Prerequisite: `onAddMediaItems` provides exact-title substring fallback (implemented).
```

- [ ] **Step 2: Commit**

```bash
git add docs/BACKLOG.md
git commit -m "docs: add onSearch semantic search to backlog"
```

---

## Manual Verification Checklist

Test with the Android Auto Desktop Head Unit (DHU). Install via Android SDK Manager → SDK Tools → Android Auto Desktop Head Unit Emulator.

**Setup:**
```bash
# Start DHU (path varies by SDK install location)
~/Android/Sdk/extras/google/auto/desktop-head-unit
```
Connect phone with USB debugging, launch "Android Auto" app on phone, DHU connects automatically.

**Browse:**
- [ ] App appears in media source list
- [ ] Tapping app shows podcast list with artwork
- [ ] Tapping a podcast shows episode list with artwork

**Playback:**
- [ ] Tapping an episode starts playback
- [ ] "Prev Chapter" and "Next Chapter" custom buttons visible in now-playing
- [ ] Tapping chapter buttons seeks to prev/next chapter

**Assistant search:**
- [ ] DHU microphone button → say "play [exact episode title]" → correct episode plays
- [ ] Say "play [podcast name]" → first episode of that podcast plays
- [ ] Say "play something completely unknown" → fallback plays first episode of first podcast
