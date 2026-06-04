# Model Download Notification Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Post an Android system notification when the Gemma model finishes downloading; tapping it re-opens the app and auto-triggers `moreAboutThis()` with the original URL.

**Architecture:** `DeepDiveRouter` (Kotlin singleton `object` with a `SharedFlow`) carries the pending URL from `MainActivity.onNewIntent()` → `PlayerViewModel`. `NotificationHelper` creates the notification channel and posts the notification. `ModelDownloadManager` grows a `pendingUrl: String` param and calls `NotificationHelper` on `Complete`. If notification permission is denied, a Snackbar fires in `PlayerScreen` on `Complete` instead.

**Tech Stack:** Android `NotificationManager`, `PendingIntent`, `NotificationChannel`; Compose `rememberLauncherForActivityResult(RequestPermission)`; Kotlin `MutableSharedFlow(replay = 1)`.

---

### Task 1: DeepDiveRouter singleton

**Files:**
- Create: `app/src/main/kotlin/com/frybynite/podcastapp/deepdive/DeepDiveRouter.kt`

No unit test — trivial singleton; integration tested implicitly in Task 4.

**Step 1: Create the file**

```kotlin
package com.frybynite.podcastapp.deepdive

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object DeepDiveRouter {
    private val _pendingUrl = MutableSharedFlow<String>(replay = 1)
    val pendingUrl: SharedFlow<String> = _pendingUrl

    fun emit(url: String) { _pendingUrl.tryEmit(url) }
}
```

`replay = 1` ensures the URL survives the brief gap between `onNewIntent` and ViewModel collection starting.

**Step 2: Verify build**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

**Step 3: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/deepdive/DeepDiveRouter.kt
git commit -m "feat: DeepDiveRouter singleton — SharedFlow for pending deep dive URL"
```

---

### Task 2: NotificationHelper

**Files:**
- Create: `app/src/main/kotlin/com/frybynite/podcastapp/deepdive/NotificationHelper.kt`

**Step 1: Create the file**

```kotlin
package com.frybynite.podcastapp.deepdive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.frybynite.podcastapp.MainActivity
import com.frybynite.podcastapp.R

object NotificationHelper {
    const val EXTRA_DEEP_DIVE_URL = "deep_dive_url"
    private const val CHANNEL_ID = "deep_dive_downloads"
    private const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Deep Dive Downloads",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Notifies when the AI model is ready for deep dives" }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun postReady(context: Context, pendingUrl: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DEEP_DIVE_URL, pendingUrl)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_podcast_placeholder)
            .setContentTitle("AI Model Ready")
            .setContentText("Tap to start your deep dive.")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }
}
```

**Step 2: Verify build**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

**Step 3: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/deepdive/NotificationHelper.kt
git commit -m "feat: NotificationHelper — channel creation and ready notification"
```

---

### Task 3: ModelDownloadManager — add pendingUrl + notify on Complete

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/deepdive/ModelDownloadManager.kt`

**Step 1: Update `downloadModel` signature and add notification call**

Change the `downloadModel()` signature to accept `pendingUrl: String` and call `NotificationHelper.postReady` on `Complete`.

Replace the entire `downloadModel` function:

```kotlin
suspend fun downloadModel(pendingUrl: String) = withContext(Dispatchers.IO) {
    val dest = context.filesDir.resolve("models/gemma-2b-it-int4.bin")
    dest.parentFile?.mkdirs()
    _state.value = ModelDownloadState.Downloading(0f)
    runCatching {
        val response = client.newCall(Request.Builder().url(MODEL_URL).build()).execute()
        val body = response.body ?: error("Empty response body")
        val total = body.contentLength()
        val tmp = File("${dest.absolutePath}.tmp")
        var downloaded = 0L
        tmp.outputStream().use { out ->
            body.byteStream().use { src ->
                val buf = ByteArray(8192)
                var n: Int
                while (src.read(buf).also { n = it } != -1) {
                    out.write(buf, 0, n)
                    downloaded += n
                    if (total > 0) _state.value = ModelDownloadState.Downloading(downloaded.toFloat() / total)
                }
            }
        }
        tmp.renameTo(dest)
        _state.value = ModelDownloadState.Complete
        NotificationHelper.postReady(context, pendingUrl)
    }.onFailure { e ->
        _state.value = ModelDownloadState.Failed(e.message ?: "Download failed")
    }
}
```

Also add `NotificationHelper.createChannel(context)` call. Add it in an `init` block:

```kotlin
init {
    NotificationHelper.createChannel(context)
}
```

**Step 2: Verify build**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```

Expected: FAIL — `PlayerViewModel.downloadModel()` still calls the old no-arg signature. That's expected; fix in Task 4.

**Step 3: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/deepdive/ModelDownloadManager.kt
git commit -m "feat: ModelDownloadManager posts notification on download complete"
```

---

### Task 4: PlayerViewModel — pendingDeepDiveUrl + collect DeepDiveRouter

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/ui/player/PlayerViewModel.kt`

**Step 1: Add import and pendingDeepDiveUrl field**

Add to imports:
```kotlin
import com.frybynite.podcastapp.deepdive.DeepDiveRouter
import kotlinx.coroutines.flow.collectLatest
```

Add field after `pendingTtsFile`:
```kotlin
private var pendingDeepDiveUrl: String? = null
```

**Step 2: Save URL before model check in moreAboutThis()**

In `moreAboutThis()`, after `resolvedUrl` is determined and before the `isModelAvailable()` check, add:
```kotlin
pendingDeepDiveUrl = resolvedUrl
```

So the function becomes:
```kotlin
fun moreAboutThis(url: String? = null) {
    val resolvedUrl = url
        ?: _chapters.value.getOrNull(_currentChapterIndex.value)?.url
        ?: run {
            _deepDiveState.value = DeepDiveState.Error("No link for this segment")
            return
        }
    pendingDeepDiveUrl = resolvedUrl
    if (!summarizer.isModelAvailable()) {
        _deepDiveState.value = DeepDiveState.ModelRequired
        return
    }
    // ... rest unchanged
```

**Step 3: Fix downloadModel() to pass pendingDeepDiveUrl**

Replace:
```kotlin
fun downloadModel() { viewModelScope.launch { modelDownloadManager.downloadModel() } }
```

With:
```kotlin
fun downloadModel() {
    viewModelScope.launch {
        modelDownloadManager.downloadModel(pendingDeepDiveUrl ?: "")
    }
}
```

**Step 4: Collect DeepDiveRouter in init**

Add an `init` block after the class fields (before `connect()`):

```kotlin
init {
    viewModelScope.launch {
        DeepDiveRouter.pendingUrl.collectLatest { url ->
            if (url.isNotEmpty()) moreAboutThis(url)
        }
    }
}
```

**Step 5: Verify build**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

**Step 6: Run tests**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -8
```

Expected: `BUILD SUCCESSFUL`

**Step 7: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/ui/player/PlayerViewModel.kt
git commit -m "feat: PlayerViewModel saves pendingDeepDiveUrl, passes to download, collects DeepDiveRouter"
```

---

### Task 5: MainActivity — handle onNewIntent and onCreate deep dive URL

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/MainActivity.kt`

**Step 1: Update MainActivity**

Replace the entire file:

```kotlin
package com.frybynite.podcastapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.frybynite.podcastapp.deepdive.DeepDiveRouter
import com.frybynite.podcastapp.deepdive.NotificationHelper
import com.frybynite.podcastapp.ui.PodcastNavGraph
import com.frybynite.podcastapp.ui.theme.PodcastAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PodcastAppTheme {
                PodcastNavGraph()
            }
        }
        handleDeepDiveIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepDiveIntent(intent)
    }

    private fun handleDeepDiveIntent(intent: Intent?) {
        val url = intent?.getStringExtra(NotificationHelper.EXTRA_DEEP_DIVE_URL) ?: return
        if (url.isNotEmpty()) DeepDiveRouter.emit(url)
    }
}
```

**Step 2: Verify build**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

**Step 3: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/MainActivity.kt
git commit -m "feat: MainActivity routes deep dive URL from notification intent to DeepDiveRouter"
```

---

### Task 6: PlayerScreen — notification permission request + Snackbar fallback

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/ui/player/PlayerScreen.kt`

**Step 1: Add imports**

Add to existing imports:
```kotlin
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.core.content.ContextCompat
```

**Step 2: Add SnackbarHostState and permission launcher before the existing voiceLauncher**

After the `snapHoverIdx` state declaration, add:

```kotlin
val snackbarHostState = remember { SnackbarHostState() }

val hasNotificationPermission = remember {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else true
}
var notificationPermissionGranted by remember { mutableStateOf(hasNotificationPermission) }

val notificationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted -> notificationPermissionGranted = granted }
```

**Step 3: Add Snackbar fallback on download complete**

After the existing `LaunchedEffect(isPlaying)` block, add:

```kotlin
LaunchedEffect(modelDownloadState) {
    if (modelDownloadState is com.frybynite.podcastapp.deepdive.ModelDownloadState.Complete
        && !notificationPermissionGranted) {
        snackbarHostState.showSnackbar("AI model ready — try \"More about this\" again")
    }
}
```

**Step 4: Wire permission request in download dialog + add SnackbarHost to Scaffold**

Replace the existing download `AlertDialog` confirm button onClick:
```kotlin
TextButton(onClick = {
    vm.downloadModel()
    vm.dismissDeepDiveError()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionGranted) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}) { Text("Download") }
```

Add `snackbarHost` param to `Scaffold`:
```kotlin
Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = { ... },
    ...
)
```

**Step 5: Verify build**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

**Step 6: Run full test suite**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -8
```

Expected: `BUILD SUCCESSFUL`

**Step 7: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/ui/player/PlayerScreen.kt
git commit -m "feat: request POST_NOTIFICATIONS on download, Snackbar fallback if denied"
```
