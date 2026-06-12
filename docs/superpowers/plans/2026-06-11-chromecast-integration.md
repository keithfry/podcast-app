# Chromecast Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cast podcast audio to Chromecast devices using a Styled Media Receiver, with full Tier 2 voice command parity (including chapter navigation) while casting.

**Architecture:** `media3-cast` `CastPlayer` swaps into `PlaybackService` as the active player when a Cast session starts. `CastContext` is initialized in `PodcastApplication`. `PlayerViewModel` exposes `isCasting: StateFlow<Boolean>`. `MediaRouteButton` (wrapped in `AndroidView`) appears in `PlayerScreen` and `EpisodeListScreen` toolbars.

**Tech Stack:** `androidx.media3:media3-cast`, `androidx.mediarouter:mediarouter`, `com.google.android.gms:play-services-cast-framework`, Hilt, Jetpack Compose

---

## File Map

| Action | File | Purpose |
|--------|------|---------|
| Modify | `gradle/libs.versions.toml` | Add cast, mediarouter, play-services-cast versions + entries |
| Modify | `app/build.gradle.kts` | Add dependencies + `CAST_APP_ID` BuildConfig field |
| Modify | `local.properties` | Add `CAST_APP_ID=CC1AD845` (dev default) |
| Create | `app/src/main/kotlin/com/frybynite/podcastapp/cast/CastOptionsProvider.kt` | Cast SDK entry point |
| Modify | `app/src/main/AndroidManifest.xml` | Declare `CastOptionsProvider` meta-data |
| Modify | `app/src/main/kotlin/com/frybynite/podcastapp/PodcastApplication.kt` | Init `CastContext` on main thread |
| Modify | `app/src/main/kotlin/com/frybynite/podcastapp/service/PlaybackService.kt` | Dual-player, `SessionManagerListener`, `MediaInfo` builder |
| Modify | `app/src/main/kotlin/com/frybynite/podcastapp/ui/player/PlayerViewModel.kt` | `isCasting: StateFlow<Boolean>` |
| Modify | `app/src/main/kotlin/com/frybynite/podcastapp/ui/player/PlayerScreen.kt` | `MediaRouteButton`, cast badge, AAOS guard |
| Modify | `app/src/main/kotlin/com/frybynite/podcastapp/ui/episodes/EpisodeListScreen.kt` | `MediaRouteButton` in toolbar |

---

### Task 1: Add dependencies and BuildConfig field

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `local.properties`

- [ ] **Step 1: Add library entries to `gradle/libs.versions.toml`**

In the `[versions]` section, add:
```toml
mediarouter = "1.7.0"
cast-framework = "21.5.0"
```

In the `[libraries]` section, add:
```toml
media3-cast = { group = "androidx.media3", name = "media3-cast", version.ref = "media3" }
mediarouter = { group = "androidx.mediarouter", name = "mediarouter", version.ref = "mediarouter" }
play-services-cast-framework = { group = "com.google.android.gms", name = "play-services-cast-framework", version.ref = "cast-framework" }
```

- [ ] **Step 2: Add dependencies to `app/build.gradle.kts`**

In the `dependencies` block, add alongside existing media3 entries:
```kotlin
implementation(libs.media3.cast)
implementation(libs.mediarouter)
implementation(libs.play.services.cast.framework)
```

In `defaultConfig`, add alongside existing `buildConfigField` entries:
```kotlin
buildConfigField("String", "CAST_APP_ID", "\"${localProperties["CAST_APP_ID"] ?: ""}\"")
```

- [ ] **Step 3: Add app ID to `local.properties`**

Append to `local.properties`:
```
CAST_APP_ID=CC1AD845
```

`CC1AD845` is Google's Default Media Receiver — works immediately for dev. Replace with your Styled Receiver app ID once registered in the Cast Developer Console.

- [ ] **Step 4: Build to confirm dependencies resolve**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL, no unresolved dependency errors.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts local.properties
git commit -m "build: add media3-cast, mediarouter, cast-framework dependencies"
```

---

### Task 2: CastOptionsProvider and manifest declaration

**Files:**
- Create: `app/src/main/kotlin/com/frybynite/podcastapp/cast/CastOptionsProvider.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create `CastOptionsProvider.kt`**

Create `app/src/main/kotlin/com/frybynite/podcastapp/cast/CastOptionsProvider.kt`:

```kotlin
package com.frybynite.podcastapp.cast

import android.content.Context
import com.frybynite.podcastapp.BuildConfig
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.media.CastMediaControlIntent

class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        val appId = BuildConfig.CAST_APP_ID.ifBlank {
            CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
        }
        return CastOptions.Builder()
            .setReceiverApplicationId(appId)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
```

- [ ] **Step 2: Declare `CastOptionsProvider` in `AndroidManifest.xml`**

Inside `<application>`, add after the existing `<meta-data>` for `com.google.android.gms.car.application`:

```xml
        <meta-data
            android:name="com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME"
            android:value="com.frybynite.podcastapp.cast.CastOptionsProvider" />
```

- [ ] **Step 3: Build**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/cast/CastOptionsProvider.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add CastOptionsProvider and manifest declaration"
```

---

### Task 3: Initialize CastContext in PodcastApplication

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/PodcastApplication.kt`

`CastContext` must be initialized on the main thread before any Cast API is used. `PodcastApplication.onCreate()` is the right place.

- [ ] **Step 1: Update `PodcastApplication.kt`**

Replace the entire file content:

```kotlin
package com.frybynite.podcastapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.android.gms.cast.framework.CastContext
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PodcastApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        CastContext.getSharedInstance(this)
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/PodcastApplication.kt
git commit -m "feat: initialize CastContext in PodcastApplication"
```

---

### Task 4: Dual-player setup in PlaybackService

This is the core of the integration. `PlaybackService` gains a `CastPlayer`, a `SessionManagerListener`, and a helper to build `MediaInfo` for the Cast receiver.

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/service/PlaybackService.kt`

Read the current file before editing — it is large and has been modified by previous tasks.

- [ ] **Step 1: Add imports to `PlaybackService.kt`**

Add these imports alongside the existing ones at the top of the file:

```kotlin
import android.net.Uri
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage
```

- [ ] **Step 2: Add `CastPlayer` field and `activePlayer` reference**

Inside `PlaybackService`, add fields alongside the existing `player: ExoPlayer` and `mediaLibrarySession: MediaLibrarySession` declarations:

```kotlin
private lateinit var castPlayer: CastPlayer
private var activePlayer: androidx.media3.common.Player? = null
```

- [ ] **Step 3: Add `buildMediaInfo` helper**

Add this private function inside `PlaybackService` (outside `callback`, below `onDestroy`):

```kotlin
private fun buildMediaInfo(audioUrl: String): MediaInfo {
    val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
        putString(MediaMetadata.KEY_TITLE, mediaLibrarySession.player.mediaMetadata.title?.toString() ?: "")
        putString(MediaMetadata.KEY_ARTIST, mediaLibrarySession.player.mediaMetadata.artist?.toString() ?: "")
        mediaLibrarySession.player.mediaMetadata.artworkUri?.let { uri ->
            addImage(WebImage(uri))
        }
    }
    return MediaInfo.Builder(audioUrl)
        .setContentType("audio/mpeg")
        .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
        .setMetadata(metadata)
        .build()
}
```

- [ ] **Step 4: Add `SessionManagerListener` and `switchToPlayer` helper**

Add these inside `PlaybackService` (before `onCreate`):

```kotlin
private fun switchToPlayer(newPlayer: androidx.media3.common.Player) {
    val currentPosition = activePlayer?.currentPosition ?: 0L
    val currentItem = activePlayer?.currentMediaItem
    val wasPlaying = activePlayer?.isPlaying ?: false
    activePlayer?.stop()

    activePlayer = newPlayer
    mediaLibrarySession.release()
    mediaLibrarySession = MediaLibrarySession.Builder(this, newPlayer, callback)
        .setSessionActivity(
            android.app.PendingIntent.getActivity(
                this, 0,
                android.content.Intent(this, com.frybynite.podcastapp.MainActivity::class.java),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    if (currentItem != null) {
        newPlayer.setMediaItem(currentItem, currentPosition)
        newPlayer.prepare()
        if (wasPlaying) newPlayer.play()
    }
}

private val castSessionListener = object : SessionManagerListener<CastSession> {
    override fun onSessionStarted(session: CastSession, sessionId: String) {
        val audioUrl = activePlayer?.currentMediaItem?.mediaId ?: return
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(buildMediaInfo(audioUrl))
            .setCurrentTime(activePlayer?.currentPosition ?: 0L)
            .setAutoplay(activePlayer?.isPlaying ?: true)
            .build()
        castPlayer.loadItem(request.mediaInfo!!, request.currentTime)
        switchToPlayer(castPlayer)
    }

    override fun onSessionEnded(session: CastSession, error: Int) {
        switchToPlayer(player)
    }

    override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {}
    override fun onSessionResumeFailed(session: CastSession, error: Int) {}
    override fun onSessionSuspended(session: CastSession, reason: Int) {}
    override fun onSessionStartFailed(session: CastSession, error: Int) {}
    override fun onSessionStarting(session: CastSession) {}
    override fun onSessionEnding(session: CastSession) {}
    override fun onSessionResuming(session: CastSession, sessionId: String) {}
}
```

- [ ] **Step 5: Initialize `CastPlayer` and register listener in `onCreate`**

In `PlaybackService.onCreate()`, after `player = ExoPlayer.Builder(...).build()` and before building `mediaLibrarySession`, add:

```kotlin
castPlayer = CastPlayer(CastContext.getSharedInstance(this))
castPlayer.setSessionAvailabilityListener(object : SessionAvailabilityListener {
    override fun onCastSessionAvailable() {} // handled by SessionManagerListener
    override fun onCastSessionUnavailable() {}
})
activePlayer = player
CastContext.getSharedInstance(this).sessionManager
    .addSessionManagerListener(castSessionListener, CastSession::class.java)
```

Change the `mediaLibrarySession` build to use `activePlayer!!` instead of `player`:

```kotlin
mediaLibrarySession = MediaLibrarySession.Builder(this, activePlayer!!, callback)
    .setSessionActivity(sessionActivity)
    .build()
```

- [ ] **Step 6: Unregister listener in `onDestroy`**

In `PlaybackService.onDestroy()`, before `mediaLibrarySession.release()`, add:

```kotlin
CastContext.getSharedInstance(this).sessionManager
    .removeSessionManagerListener(castSessionListener, CastSession::class.java)
castPlayer.release()
```

- [ ] **Step 7: Update `onMediaItemTransition` to reload Cast metadata**

In `PlaybackService.player.addListener`, the existing `onMediaItemTransition` loads chapters. After the existing chapter-loading code, add Cast metadata reload:

```kotlin
override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
    val audioUrl = mediaItem?.mediaId ?: return
    Log.i(TAG, "onMediaItemTransition: audioUrl=$audioUrl title=${mediaItem?.mediaMetadata?.title} reason=$reason")
    chaptersJob?.cancel()
    chaptersJob = serviceScope.launch {
        chapterRepo.chaptersForEpisode(audioUrl).collect { list ->
            Log.d(TAG, "chapters updated: count=${list.size} for $audioUrl")
            chapters = list
        }
    }
    // Reload Cast metadata if currently casting
    if (activePlayer === castPlayer && castPlayer.isCastSessionAvailable) {
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(buildMediaInfo(audioUrl))
            .setCurrentTime(0L)
            .setAutoplay(true)
            .build()
        castPlayer.loadItem(request.mediaInfo!!, 0L)
    }
}
```

- [ ] **Step 8: Build**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL, no unresolved references.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/service/PlaybackService.kt
git commit -m "feat: add CastPlayer dual-player setup to PlaybackService"
```

---

### Task 5: isCasting StateFlow in PlayerViewModel

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/ui/player/PlayerViewModel.kt`

- [ ] **Step 1: Add `isCasting` StateFlow**

In `PlayerViewModel`, add the following field alongside the other `StateFlow` declarations (after `_episodeTitle`):

```kotlin
private val _isCasting = MutableStateFlow(false)
val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()
```

- [ ] **Step 2: Observe CastContext session state**

In `PlayerViewModel`, add a `SessionManagerListener` that updates `_isCasting`. Add this private field:

```kotlin
private val castSessionListener = object : com.google.android.gms.cast.framework.SessionManagerListener<com.google.android.gms.cast.framework.CastSession> {
    override fun onSessionStarted(session: com.google.android.gms.cast.framework.CastSession, sessionId: String) {
        _isCasting.value = true
    }
    override fun onSessionEnded(session: com.google.android.gms.cast.framework.CastSession, error: Int) {
        _isCasting.value = false
    }
    override fun onSessionResumed(session: com.google.android.gms.cast.framework.CastSession, wasSuspended: Boolean) {
        _isCasting.value = true
    }
    override fun onSessionResumeFailed(session: com.google.android.gms.cast.framework.CastSession, error: Int) {}
    override fun onSessionSuspended(session: com.google.android.gms.cast.framework.CastSession, reason: Int) {}
    override fun onSessionStartFailed(session: com.google.android.gms.cast.framework.CastSession, error: Int) {}
    override fun onSessionStarting(session: com.google.android.gms.cast.framework.CastSession) {}
    override fun onSessionEnding(session: com.google.android.gms.cast.framework.CastSession) {}
    override fun onSessionResuming(session: com.google.android.gms.cast.framework.CastSession, sessionId: String) {}
}
```

- [ ] **Step 3: Register and unregister the listener**

In `PlayerViewModel.init {}` block (or add one if absent), register the listener:

```kotlin
init {
    val castContext = com.google.android.gms.cast.framework.CastContext.getSharedInstance(context)
    _isCasting.value = castContext.currentCastSession != null
    castContext.sessionManager.addSessionManagerListener(
        castSessionListener,
        com.google.android.gms.cast.framework.CastSession::class.java
    )
}
```

In `PlayerViewModel.onCleared()`, unregister:

```kotlin
override fun onCleared() {
    // existing cleanup...
    runCatching {
        com.google.android.gms.cast.framework.CastContext.getSharedInstance(context)
            .sessionManager.removeSessionManagerListener(
                castSessionListener,
                com.google.android.gms.cast.framework.CastSession::class.java
            )
    }
    super.onCleared()
}
```

- [ ] **Step 4: Build**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/ui/player/PlayerViewModel.kt
git commit -m "feat: add isCasting StateFlow to PlayerViewModel"
```

---

### Task 6: MediaRouteButton and cast badge in PlayerScreen

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/ui/player/PlayerScreen.kt`

- [ ] **Step 1: Add imports**

Add these imports at the top of `PlayerScreen.kt`:

```kotlin
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import androidx.compose.material.icons.filled.Cast
```

- [ ] **Step 2: Collect `isCasting` state**

In `PlayerScreen` composable, after the existing `collectAsStateWithLifecycle` calls, add:

```kotlin
val isCasting by vm.isCasting.collectAsStateWithLifecycle()
```

- [ ] **Step 3: Add `MediaRouteButton` to `TopAppBar` actions**

The existing `actions` block in `TopAppBar` has a sleep timer button. Add the cast button before it, guarded by `isAutomotive`:

```kotlin
actions = {
    if (!isAutomotive) {
        AndroidView(
            factory = { ctx ->
                MediaRouteButton(ctx).also { button ->
                    CastButtonFactory.setUpMediaRouteButton(ctx, button)
                }
            },
            modifier = Modifier.size(48.dp)
        )
    }
    IconButton(onClick = { showSleepSheet = true }) {
        // existing sleep timer button content unchanged
    }
}
```

- [ ] **Step 4: Add cast badge next to artwork**

Find the artwork display in `PlayerScreen` (look for `AsyncImage` or `Image` showing `podcastImageUrl`). Wrap it in a `Box` and overlay a cast icon badge when casting:

```kotlin
Box {
    // existing artwork composable here (AsyncImage/Image — do not change it)
    if (isCasting) {
        Icon(
            Icons.Filled.Cast,
            contentDescription = "Casting",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(24.dp)
                .padding(2.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
```

- [ ] **Step 5: Build**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/ui/player/PlayerScreen.kt
git commit -m "feat: add MediaRouteButton and cast badge to PlayerScreen"
```

---

### Task 7: MediaRouteButton in EpisodeListScreen

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/ui/episodes/EpisodeListScreen.kt`

- [ ] **Step 1: Add imports**

Add at the top of `EpisodeListScreen.kt`:

```kotlin
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import androidx.compose.ui.platform.LocalContext
```

- [ ] **Step 2: Add `isAutomotive` check**

In `EpisodeListScreen` composable, add after existing state collection:

```kotlin
val context = LocalContext.current
val isAutomotive = context.packageManager.hasSystemFeature("android.hardware.type.automotive")
```

- [ ] **Step 3: Add `actions` to `TopAppBar`**

The current `TopAppBar` has no `actions` block. Add one:

```kotlin
TopAppBar(
    title = { Text("Episodes") },
    navigationIcon = {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
        }
    },
    actions = {
        if (!isAutomotive) {
            AndroidView(
                factory = { ctx ->
                    MediaRouteButton(ctx).also { button ->
                        CastButtonFactory.setUpMediaRouteButton(ctx, button)
                    }
                },
                modifier = Modifier.size(48.dp)
            )
        }
    }
)
```

- [ ] **Step 4: Build**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/ui/episodes/EpisodeListScreen.kt
git commit -m "feat: add MediaRouteButton to EpisodeListScreen toolbar"
```

---

### Task 8: Manual smoke test

**Files:** None — testing only. Requires a physical Chromecast device on the same Wi-Fi network as the test phone.

- [ ] **Step 1: Build and install on device**

```bash
./scripts/device.sh
```

- [ ] **Step 2: Verify cast button appears**

Open app → navigate to a podcast's episode list → confirm cast button visible in top-right of toolbar.

Open the player → confirm cast button visible in top-right.

- [ ] **Step 3: Verify cast button hidden on AAOS**

```bash
./scripts/aaos.sh
```

Open episode list and player on AAOS emulator — cast button must NOT appear.

- [ ] **Step 4: Cast an episode**

On phone: tap cast button → select Chromecast from picker → episode audio transfers to Chromecast.

Verify:
- Cast badge appears on artwork in `PlayerScreen`
- TV shows podcast artwork + episode title (if using Styled Receiver; Default Receiver shows ambient backdrop)
- Phone stays on `PlayerScreen`

- [ ] **Step 5: Test voice chapter navigation while casting**

While casting: tap mic button → say "next section" → chapter advances on Chromecast (seek happens).

Repeat for "previous section".

- [ ] **Step 6: Test session disconnect**

On phone: tap cast button → disconnect. Verify audio resumes on phone from the same position.

- [ ] **Step 7: Test downloaded episode cast**

Download an episode. While episode is downloaded, start casting it. Verify it streams from remote URL (not silent/failed). Check `PlaybackSvc` logcat — `mediaId` should be the `audioUrl`, not a `file://` path.

- [ ] **Step 8: Commit any fixes found during testing**

```bash
git add -p
git commit -m "fix: <describe fix>"
```

---

## Testing Notes

- `MediaRouteButton` only shows when a Cast-compatible device is found on the network. On a network with no Chromecast, the button is invisible — this is correct behavior.
- Chapter navigation (`CMD_NEXT_CHAPTER` / `CMD_PREV_CHAPTER`) calls `player.seekTo()` which `CastPlayer` handles natively. No additional changes needed for voice commands.
- The `CAST_APP_ID=CC1AD845` default works for all testing. Register a Styled Receiver app ID in the [Cast Developer Console](https://cast.google.com/publish) before production release, then update `local.properties`.
- If `CastContext.getSharedInstance()` throws on first call, ensure `play-services-cast-framework` dependency is correctly resolved and Google Play Services is available on the test device.
