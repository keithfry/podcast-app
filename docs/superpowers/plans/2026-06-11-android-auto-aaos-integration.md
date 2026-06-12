# Android Auto / AAOS Integration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the podcast app discoverable and functional on Android Auto (phone projection) and Android Automotive OS (AAOS emulator) using the existing `MediaLibraryService` architecture — no Car App Library needed.

**Architecture:** Android Auto and AAOS both consume media apps via `MediaBrowserService`/`MediaLibraryService`. The existing `PlaybackService` already implements this correctly. The missing pieces are: manifest declarations, artwork URIs on media items, browse tree content-style hints, and a per-podcast image lookup when building episode items.

**Tech Stack:** Media3 `MediaLibrarySession`, `MediaLibraryService`, AndroidManifest meta-data, `res/xml/automotive_app_desc.xml`

---

## File Map

| Action | File | Purpose |
|--------|------|---------|
| Create | `app/src/main/res/xml/automotive_app_desc.xml` | Declares app as media app to Android Auto |
| Modify | `app/src/main/AndroidManifest.xml` | Add Auto meta-data + AAOS `uses-feature` |
| Modify | `app/src/main/kotlin/com/frybynite/podcastapp/service/PlaybackService.kt` | Artwork on media items, content-style hints, recent root handling |

---

### Task 1: Declare Android Auto media support

**Files:**
- Create: `app/src/main/res/xml/automotive_app_desc.xml`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create res/xml directory if missing**

```bash
mkdir -p app/src/main/res/xml
```

- [ ] **Step 2: Create automotive_app_desc.xml**

Create `app/src/main/res/xml/automotive_app_desc.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<automotiveApp>
    <uses name="media"/>
</automotiveApp>
```

- [ ] **Step 3: Add meta-data and AAOS uses-feature to AndroidManifest.xml**

In `app/src/main/AndroidManifest.xml`, add inside `<application>` tag, directly after the opening `<application` block (before the first `<activity>`):

```xml
        <meta-data
            android:name="com.google.android.gms.car.application"
            android:resource="@xml/automotive_app_desc" />
```

Also add inside `<manifest>` (before `<application>`), the AAOS feature declaration:

```xml
    <uses-feature
        android:name="android.hardware.type.automotive"
        android:required="false" />
```

`required="false"` keeps the app installable on phones.

- [ ] **Step 4: Build to confirm no manifest errors**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL, no manifest merge errors.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/xml/automotive_app_desc.xml app/src/main/AndroidManifest.xml
git commit -m "feat: declare Android Auto and AAOS media support in manifest"
```

---

### Task 2: Add artwork and content-style hints to media items

Android Auto displays podcast cover art and uses content-style hints to render lists vs. grids correctly. `Podcast.imageUrl` exists; episodes inherit their podcast's image.

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/service/PlaybackService.kt`

- [ ] **Step 1: Update `Podcast.toMediaItem()` to include artwork**

In `PlaybackService.kt`, replace the `Podcast.toMediaItem()` extension at the bottom of the file:

```kotlin
private fun Podcast.toMediaItem() = MediaItem.Builder()
    .setMediaId("${PlaybackService.PODCAST_PREFIX}$feedUrl")
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(author)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST)
            .setArtworkUri(imageUrl?.let { android.net.Uri.parse(it) })
            .setExtras(Bundle().apply {
                putInt(
                    "androidx.media3.session.MediaMetadataCompat.CONTENT_STYLE_BROWSABLE_HINT",
                    CONTENT_STYLE_LIST_ITEM_HINT_VALUE
                )
            })
            .build()
    ).build()
```

Add the constant at the top of the companion object in `PlaybackService`:

```kotlin
private const val CONTENT_STYLE_LIST_ITEM_HINT_VALUE = 1
```

- [ ] **Step 2: Update `Episode.toMediaItem()` to accept and use podcast artwork**

Replace the `Episode.toMediaItem()` extension at the bottom of the file:

```kotlin
private fun Episode.toMediaItem(podcastImageUrl: String?) = MediaItem.Builder()
    .setMediaId(audioUrl)
    .setUri(audioUrl)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .setArtworkUri(podcastImageUrl?.let { android.net.Uri.parse(it) })
            .build()
    ).build()
```

- [ ] **Step 3: Update `onGetChildren` to pass podcast imageUrl to episodes**

In `PlaybackService.kt`, update the `onGetChildren` implementation inside `callback`. Replace the `parentId.startsWith(PODCAST_PREFIX)` branch:

```kotlin
parentId.startsWith(PODCAST_PREFIX) -> {
    val feedUrl = parentId.removePrefix(PODCAST_PREFIX)
    val podcast = podcastRepo.podcasts.first().find { it.feedUrl == feedUrl }
    val episodes = podcastRepo.episodesForPodcast(feedUrl).first()
    LibraryResult.ofItemList(
        episodes.map { it.toMediaItem(podcast?.imageUrl) },
        params
    )
}
```

- [ ] **Step 4: Build**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL, no unresolved references.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/service/PlaybackService.kt
git commit -m "feat: add podcast artwork and content-style hints to Auto media items"
```

---

### Task 3: Handle recent-episodes root for Android Auto home screen

Android Auto requests the browse root with `LibraryParams.isRecent = true` to populate the "recently played" shelf on the home screen. Without this, the home screen shelf is empty.

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/service/PlaybackService.kt`

- [ ] **Step 1: Add a RECENT_ROOT constant**

In `PlaybackService.companion object`, add:

```kotlin
const val RECENT_ROOT = "root_recent"
```

- [ ] **Step 2: Update `onGetLibraryRoot` to return a recent root when requested**

Replace the existing `onGetLibraryRoot` override inside `callback`:

```kotlin
override fun onGetLibraryRoot(
    session: MediaLibrarySession,
    browser: MediaSession.ControllerInfo,
    params: LibraryParams?
): ListenableFuture<LibraryResult<MediaItem>> {
    val rootId = if (params?.isRecent == true) RECENT_ROOT else BROWSE_ROOT
    return Futures.immediateFuture(
        LibraryResult.ofItem(
            MediaItem.Builder()
                .setMediaId(rootId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .setTitle(if (rootId == RECENT_ROOT) "Recent" else "Podcasts")
                        .build()
                ).build(), params
        )
    )
}
```

- [ ] **Step 3: Handle RECENT_ROOT in `onGetChildren`**

In `onGetChildren` inside `callback`, add a branch for `RECENT_ROOT` before the existing `when` branches:

```kotlin
parentId == RECENT_ROOT -> {
    // Return the most recently published episode across all podcasts
    val podcasts = podcastRepo.podcasts.first()
    val recent = podcasts.flatMap { podcast ->
        podcastRepo.episodesForPodcast(podcast.feedUrl).first()
            .take(1)
            .map { it.toMediaItem(podcast.imageUrl) }
    }.sortedByDescending { it.mediaMetadata.recordingYear }.take(5)
    LibraryResult.ofItemList(recent, params)
}
```

Note: `sortedByDescending` here is a best-effort sort — episode pubDate is not in MediaMetadata. A future improvement would sort by `Episode.pubDate` before converting. For now this gives Auto *something* to show.

- [ ] **Step 4: Fix the sort to use Episode.pubDate**

The sort above operates on `MediaItem` which has no pubDate. Replace the `RECENT_ROOT` branch with a version that sorts before mapping:

```kotlin
parentId == RECENT_ROOT -> {
    val podcasts = podcastRepo.podcasts.first()
    val recent = podcasts.flatMap { podcast ->
        val episodes = podcastRepo.episodesForPodcast(podcast.feedUrl).first()
        episodes.map { Pair(podcast, it) }
    }
        .sortedByDescending { (_, episode) -> episode.pubDate }
        .take(5)
        .map { (podcast, episode) -> episode.toMediaItem(podcast.imageUrl) }
    LibraryResult.ofItemList(recent, params)
}
```

- [ ] **Step 5: Build**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/service/PlaybackService.kt
git commit -m "feat: return recent episodes for Android Auto home screen shelf"
```

---

### Task 4: Test on AAOS emulator

**Files:** None — testing only.

- [ ] **Step 1: Start AAOS emulator and install app**

```bash
./scripts/device.sh --automotive
```

Expected output ends with `Done.` and emulator shows the podcast app.

- [ ] **Step 2: Verify media browsing in AAOS**

On the emulator:
1. Open the media app drawer (grid icon on home screen)
2. Find "Podcast App"
3. Confirm podcast list loads
4. Tap a podcast → confirm episode list loads with artwork
5. Tap an episode → confirm playback starts

- [ ] **Step 3: Verify playback controls**

While playing:
- Pause/play button works
- Seek bar responds
- "Prev Chapter" / "Next Chapter" custom buttons appear (if episode has chapters)

- [ ] **Step 4: Commit if any fixes were needed during testing**

```bash
git add -p
git commit -m "fix: <describe any fix found during AAOS testing>"
```

---

## Testing Notes

- Android Auto phone projection still requires a real car or working DHU. AAOS emulator validates the media API surface which is shared between both.
- If browse tree shows empty on AAOS: check logcat for `PlaybackSvc` tag — `onGetChildren` logs are already in place.
- If artwork doesn't appear: `Podcast.imageUrl` may be null for some feeds; this is a data issue, not a code issue.
- Chapter navigation commands (`CMD_NEXT_CHAPTER` / `CMD_PREV_CHAPTER`) are custom session commands — Android Auto/AAOS may not surface them as buttons depending on platform version. Standard transport controls (play/pause/seek) always work.
