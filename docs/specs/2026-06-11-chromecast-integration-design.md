# Chromecast Integration Design

**Date:** 2026-06-11
**Status:** Approved

---

## Goal

Cast podcast audio to Chromecast devices from the Android app. The TV displays episode artwork and title via a Styled Media Receiver. All Tier 2 voice commands (including chapter navigation) work identically whether casting or not. The phone stays on the full `PlayerScreen` as a controller while audio plays on Chromecast.

---

## Architecture

### Player Swapping

`PlaybackService` holds two players — `ExoPlayer` (local) and `CastPlayer` (Cast) — with a single `activePlayer` reference. A `SessionManagerListener` registered on `CastContext` flips `activePlayer` when Cast sessions start or end.

```
CastContext (singleton, PodcastApplication.onCreate)
    └── SessionManagerListener
            ├── onSessionStarted → activePlayer = castPlayer; transfer state; rebuild MediaLibrarySession
            └── onSessionEnded   → activePlayer = exoPlayer; transfer state; rebuild MediaLibrarySession
```

State transfer copies current media item, position, and play/pause state from the outgoing player to the incoming one before switching.

`MediaLibrarySession` is rebuilt each time `activePlayer` switches (Media3 requirement). The new session is returned from `onGetSession`.

### Voice Commands & Chapter Navigation

`CMD_NEXT_CHAPTER` and `CMD_PREV_CHAPTER` SessionCommands call `player.seekTo(targetMs)`. `CastPlayer` implements the same `Player` interface as `ExoPlayer` and accepts `seekTo()` identically. No additional plumbing needed — full Tier 2 parity is automatic.

All other voice commands (seek ±30s, open link, share link) are phone-side operations unaffected by casting state.

### Cast State in ViewModel

`PlayerViewModel` exposes:
```kotlin
val isCasting: StateFlow<Boolean>
```
Derived from `CastContext.currentCastSession != null`, updated via `SessionManagerListener`. Used to show/hide the cast indicator badge in `PlayerScreen`.

---

## UI

### Cast Button

`MediaRouteButton` (from `androidx.mediarouter`) wrapped in `AndroidView` added to:
- `PlayerScreen` top bar
- `EpisodeListScreen` top bar

`MediaRouteButton` auto-shows/hides based on Chromecast availability on the network. No manual discovery code needed.

### Cast Indicator

While `isCasting == true`, a small cast icon badge appears next to episode artwork in `PlayerScreen` confirming audio routes to Chromecast.

### AAOS Guard

Cast button hidden on Android Automotive OS — same `isAutomotive` check pattern used for share buttons:
```kotlin
val isAutomotive = LocalContext.current.packageManager
    .hasSystemFeature("android.hardware.type.automotive")
if (!isAutomotive) { /* show MediaRouteButton */ }
```

---

## Styled Media Receiver

### Registration

Register a free app ID in the Google Cast SDK Developer Console. Select "Styled Media Receiver" — Google hosts the receiver web app. Configure branding (colors, logo) via the console. No custom web app to write or host.

App ID stored in `local.properties`:
```
CAST_APP_ID=XXXXXXXX
```
Exposed via `BuildConfig.CAST_APP_ID`. During development, use `CC1AD845` (Default Media Receiver) until the Styled Receiver is registered.

### MediaInfo Construction

When loading an episode into `CastPlayer`, `PlaybackService` builds:

```kotlin
MediaInfo.Builder(episode.audioUrl)
    .setContentType("audio/mpeg")
    .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
    .setMetadata(
        MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, episode.title)
            putString(MediaMetadata.KEY_ARTIST, podcast.title)
            podcast.imageUrl?.let { addImage(WebImage(Uri.parse(it))) }
        }
    )
    .build()
```

The Styled Media Receiver reads this and displays artwork + title on the TV automatically.

### Downloaded Episodes

Downloaded episodes have `file://` paths inaccessible to Cast receivers. When casting, always use `episode.audioUrl` (remote URL), not `episode.downloadPath`.

---

## Dependencies

Added to `libs.versions.toml` and `app/build.gradle.kts`:

| Artifact | Purpose |
|----------|---------|
| `androidx.media3:media3-cast` | `CastPlayer` |
| `androidx.mediarouter:mediarouter` | `MediaRouteButton` |
| `com.google.android.gms:play-services-cast-framework` | `CastContext`, session management |

### CastOptionsProvider

Small class implementing `OptionsProvider` (Cast SDK requirement):

```kotlin
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(
                BuildConfig.CAST_APP_ID.ifBlank { CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID }
            )
            .build()
    override fun getAdditionalSessionProviders(context: Context) = null
}
```

Declared in `AndroidManifest.xml`:
```xml
<meta-data
    android:name="com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME"
    android:value="com.frybynite.podcastapp.cast.CastOptionsProvider" />
```

---

## Error Handling & Edge Cases

| Scenario | Behaviour |
|----------|-----------|
| `CAST_APP_ID` empty/blank | Falls back to `CC1AD845` (Default Media Receiver) — no crash |
| Cast session drops mid-episode | `onSessionEnded` transfers position to ExoPlayer; audio resumes on phone |
| Episode switches while casting | `onMediaItemTransition` rebuilds `MediaInfo` and loads into `CastPlayer`; chapters update phone-side |
| Episode is downloaded (`file://`) | Cast uses `audioUrl` (remote) regardless of download state |
| AAOS | Cast button hidden via `isAutomotive` guard |
| No Chromecast on network | `MediaRouteButton` hidden automatically by Cast SDK |

---

## Files Changed

| Action | File | Purpose |
|--------|------|---------|
| Modify | `gradle/libs.versions.toml` | Add cast/mediarouter versions + library entries |
| Modify | `app/build.gradle.kts` | Add dependencies + `CAST_APP_ID` BuildConfig field |
| Modify | `local.properties` | Add `CAST_APP_ID=` |
| Modify | `app/src/main/AndroidManifest.xml` | Add `CastOptionsProvider` meta-data |
| Create | `.../cast/CastOptionsProvider.kt` | Cast SDK options entry point |
| Modify | `.../PodcastApplication.kt` | Init `CastContext` on main thread |
| Modify | `.../service/PlaybackService.kt` | Dual-player setup, `SessionManagerListener`, `MediaInfo` builder |
| Modify | `.../ui/player/PlayerViewModel.kt` | `isCasting: StateFlow<Boolean>` |
| Modify | `.../ui/player/PlayerScreen.kt` | `MediaRouteButton`, cast badge, AAOS guard |
| Modify | `.../ui/episodes/EpisodeListScreen.kt` | `MediaRouteButton` in toolbar |

---

## Verification Checklist

1. Chromecast visible on network → cast button appears in player and episode list
2. No Chromecast on network → cast button hidden
3. Tap cast button → device picker shows → select Chromecast → audio transfers to TV
4. TV shows podcast artwork + episode title
5. Phone stays on `PlayerScreen` while casting
6. Cast badge appears next to artwork while casting
7. Pause/play on phone → Chromecast responds
8. "Next section" / "previous section" voice commands → chapter advances on Chromecast
9. Seek ±30s → Chromecast seeks
10. Cast session disconnected → audio resumes on phone from same position
11. Switch episode while casting → new episode loads on Chromecast with correct metadata
12. Play downloaded episode while casting → streams from remote URL, not local file
13. AAOS emulator → no cast button visible
14. `CAST_APP_ID` blank → falls back to default receiver, no crash
