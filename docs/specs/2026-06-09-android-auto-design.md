# Android Auto Integration Design

**Date:** 2026-06-09
**Scope:** Media browsing + Google Assistant playback commands + chapter nav in Auto UI

## Goals

- App appears as a media source in Android Auto
- Google Assistant commands ("play Science Vs on Podcast App") route to the app and play matching episodes
- Chapter nav (prev/next) accessible from Auto's media controls as custom action buttons
- Podcast artwork visible in Auto's now-playing and browse screens

## Out of Scope

- Car App Library (no custom Auto UI screens, no in-Auto mic button)
- Episode-level search from Assistant queries (backlog: "Semantic episode search via voice")
- Episode-level artwork (podcast image used for all episodes)

## Architecture

No new classes. All changes are in `PlaybackService`, `AndroidManifest.xml`, and a new XML resource. The browse tree (`onGetLibraryRoot` / `onGetChildren`) already works correctly.

---

## Section 1: Registration & Manifest

### `res/xml/automotive_app_desc.xml` (new)

```xml
<?xml version="1.0" encoding="utf-8"?>
<automotiveApp>
    <uses name="media"/>
</automotiveApp>
```

### `AndroidManifest.xml` changes

In `<application>` block — declare Auto app descriptor:

```xml
<meta-data
    android:name="com.google.android.gms.car.application"
    android:resource="@xml/automotive_app_desc" />
```

In `<service android:name=".service.PlaybackService">` — declare media app icon for Auto:

```xml
<meta-data
    android:name="com.google.android.gms.car.service.MEDIA_APP_ICON"
    android:resource="@mipmap/ic_launcher" />
```

No new Gradle dependencies. `MediaLibraryService` (Media3) already satisfies Auto's media service contract.

---

## Section 2: Artwork

Auto displays artwork prominently in the now-playing screen and browse list. Source: podcast-level `imageUrl` (already in `PodcastEntity`, available via `PodcastRepository`).

### `Podcast.toMediaItem()` (in `PlaybackService.kt`)

Add artwork URI:

```kotlin
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

### `Episode.toMediaItem(podcastImageUrl: String?)` (in `PlaybackService.kt`)

Add optional artwork param, set if non-null:

```kotlin
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

### `onGetChildren` call site

When returning episodes, fetch the parent podcast's `imageUrl` and pass it in:

```kotlin
parentId.startsWith(PODCAST_PREFIX) -> {
    val feedUrl = parentId.removePrefix(PODCAST_PREFIX)
    val episodes = podcastRepo.episodesForPodcast(feedUrl).first()
    val podcast = podcastRepo.podcasts.first().find { it.feedUrl == feedUrl }
    LibraryResult.ofItemList(
        episodes.map { it.toMediaItem(podcast?.imageUrl) },
        params
    )
}
```

---

## Section 3: Google Assistant — Play From Search

When a user says "Hey Google, play [query] on Podcast App", Auto sends a `playFromSearch` intent. Media3 delivers this as a `MediaItem` with `requestMetadata.searchQuery` set and no URI.

### Change to `onAddMediaItems` in `PlaybackService.Callback`

Extend existing resolver to handle search queries:

```kotlin
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

private suspend fun resolveSearchQuery(query: String): MediaItem {
    val q = query.lowercase()
    val allPodcasts = podcastRepo.podcasts.first()
    for (podcast in allPodcasts) {
        val episodes = podcastRepo.episodesForPodcast(podcast.feedUrl).first()
        val match = episodes.firstOrNull { it.title.lowercase().contains(q) }
            ?: if (podcast.title.lowercase().contains(q)) episodes.firstOrNull() else null
        if (match != null) {
            return match.toMediaItem(podcast.imageUrl)
        }
    }
    // Fallback: play latest episode of first podcast
    val fallbackPodcast = allPodcasts.firstOrNull()
    val fallbackEpisode = fallbackPodcast?.let {
        podcastRepo.episodesForPodcast(it.feedUrl).first().firstOrNull()
    }
    return fallbackEpisode?.toMediaItem(fallbackPodcast?.imageUrl)
        ?: MediaItem.EMPTY
}
```

Search logic: case-insensitive substring match on episode title, then podcast title (plays latest episode). Fallback: latest episode of first podcast. No ranking — simple enough for personal use.

---

## Section 4: Chapter Nav Custom Layout in Auto

Auto's media controls show at most 3 action slots. The two existing chapter buttons fit alongside play/pause.

### Change to `onConnect` in `PlaybackService.Callback`

Auto's controller package is `com.google.android.projection.gearhead`. Custom layout already returned for all controllers — no change needed unless filtering is required. The existing `setCustomLayout` with `CMD_PREV_CHAPTER` / `CMD_NEXT_CHAPTER` buttons will render in Auto.

One verification needed at runtime: Auto must be able to resolve `android.R.drawable.ic_media_previous` / `ic_media_next` — these are system drawables, which Auto accepts. If not rendered, swap to `R.drawable.*` from the app's own resources.

No logic changes to `onCustomCommand` — chapter seek already works.

---

## Backlog Addition

Add to `BACKLOG.md` under **Voice / NLP**:

> **`onSearch` / semantic episode search from Assistant** — extend `PlaybackService.Callback.onSearch` to handle Assistant browse queries ("find episodes about AI"). Requires episode description indexing in Room and a ranking function. Prerequisite: current `onAddMediaItems` search provides exact-title fallback.

---

## Files Changed

| File | Change |
|------|--------|
| `app/src/main/res/xml/automotive_app_desc.xml` | New — Auto app descriptor |
| `app/src/main/AndroidManifest.xml` | Add `<meta-data>` for Auto descriptor and service icon |
| `app/src/main/kotlin/.../service/PlaybackService.kt` | Artwork on `MediaItem`s, `onAddMediaItems` search query resolution |
| `docs/BACKLOG.md` | Add `onSearch` follow-up item |

## Testing

- **Android Auto Desktop Head Unit (DHU):** Install DHU via Android SDK, run `desktop-head-unit` — no physical car needed
- **Browse:** Auto shows podcast list → tap podcast → episode list, artwork visible
- **Playback:** Tap episode, chapter nav buttons appear, chapter seek works
- **Assistant:** DHU supports simulated Assistant queries — test "play [episode title]" and "play [podcast name]"
- **Fallback:** Query with no match plays first episode of first podcast
