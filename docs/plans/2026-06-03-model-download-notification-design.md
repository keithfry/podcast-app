# Model Download Notification Design

## Goal

When the Gemma model finishes downloading, post an Android system notification. Tapping it brings the app to the foreground and automatically triggers `moreAboutThis()` with the URL that originally requested the deep dive.

## Data Flow

1. User triggers `moreAboutThis(url)` → model unavailable → ViewModel saves `url` as `pendingDeepDiveUrl` → `DeepDiveState.ModelRequired`
2. User confirms download dialog → `vm.downloadModel()` passes `pendingDeepDiveUrl` to `ModelDownloadManager.downloadModel(pendingUrl)`
3. On `Complete`, `ModelDownloadManager` posts a notification with `pendingUrl` embedded as an Intent extra
4. User taps notification → `MainActivity.onNewIntent()` extracts URL → emits to `DeepDiveRouter` singleton
5. `PlayerViewModel` collects `DeepDiveRouter` flow → calls `moreAboutThis(url)` automatically

## Permission

Request `POST_NOTIFICATIONS` (Android 13+) at the moment the user confirms download in the dialog. If denied, fall back to an in-app Snackbar ("Model downloaded — try 'More about this' again") on `Complete`.

## New Components

### `DeepDiveRouter` (singleton object)
```kotlin
object DeepDiveRouter {
    private val _pendingUrl = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val pendingUrl: SharedFlow<String> = _pendingUrl
    fun emit(url: String) { _pendingUrl.tryEmit(url) }
}
```

### `NotificationHelper`
Utility object. Creates `"deep_dive_downloads"` NotificationChannel on first call. Posts notification with title "AI Model Ready", body "Tap to start your deep dive.", and a `PendingIntent` to `MainActivity` with `FLAG_SINGLE_TOP` + `EXTRA_DEEP_DIVE_URL = url`.

## Modified Components

### `ModelDownloadManager`
- `downloadModel(pendingUrl: String)` — stores `pendingUrl`, calls `NotificationHelper.postReady(pendingUrl)` on `Complete`

### `PlayerViewModel`
- Add `private var pendingDeepDiveUrl: String?` — set in `moreAboutThis()` before checking model availability
- `downloadModel()` passes `pendingDeepDiveUrl ?: ""` to `ModelDownloadManager`
- In `init {}`, collect `DeepDiveRouter.pendingUrl` → call `moreAboutThis(url)`

### `MainActivity`
- Override `onNewIntent(intent)` — extract `EXTRA_DEEP_DIVE_URL`, emit to `DeepDiveRouter`

### `PlayerScreen`
- Request `POST_NOTIFICATIONS` permission before calling `vm.downloadModel()` in the download dialog confirm button
- On `ModelDownloadState.Complete`, if notification permission was denied, show Snackbar

## Manifest
- Add `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`
