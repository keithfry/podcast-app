# More About This — Execution Progress

Worktree: `worktrees/feat-more-about-this` (branch: `feat/more-about-this`)

## Completed

- ✅ Task 1: Dependencies (MediaPipe tasks-genai 0.10.22, Jsoup 1.17.2, mockk already present)
- ✅ Task 2: VoiceCommand.MORE_ABOUT_THIS + phrase matching + VoiceCommandHandlerTest (30 tests pass)
- ✅ Task 3: UrlContentFetcher (OkHttp + Jsoup, response.use{} fix, 3 tests pass)

## Remaining

### Task 4: TextSummarizer + GemmaTextSummarizer + ModelDownloadManager

**Files:**
- Create: `app/src/main/kotlin/com/frybynite/podcastapp/deepdive/TextSummarizer.kt`
- Create: `app/src/main/kotlin/com/frybynite/podcastapp/deepdive/GemmaTextSummarizer.kt`
- Create: `app/src/main/kotlin/com/frybynite/podcastapp/deepdive/ModelDownloadManager.kt`
- Test: `app/src/test/kotlin/com/frybynite/podcastapp/deepdive/TextSummarizerContractTest.kt`

TextSummarizer.kt:
```kotlin
package com.frybynite.podcastapp.deepdive

interface TextSummarizer {
    fun isModelAvailable(): Boolean
    suspend fun summarize(text: String): String
}
```

TextSummarizerContractTest.kt:
```kotlin
package com.frybynite.podcastapp.deepdive

import org.junit.Test
import kotlin.test.assertTrue

class TextSummarizerContractTest {
    private val summarizer: TextSummarizer = object : TextSummarizer {
        override fun isModelAvailable() = true
        override suspend fun summarize(text: String) = "Summary: ${text.take(10)}"
    }

    @Test fun `summarize returns non-empty string`() {
        val result = summarizer.summarize("A long article about technology.")
        assertTrue(result.isNotEmpty())
    }

    @Test fun `isModelAvailable returns boolean`() {
        assertTrue(summarizer.isModelAvailable())
    }
}
```

GemmaTextSummarizer.kt:
```kotlin
package com.frybynite.podcastapp.deepdive

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GemmaTextSummarizer @Inject constructor(
    @ApplicationContext private val context: Context
) : TextSummarizer {

    private var inference: LlmInference? = null

    val modelFile get() = context.filesDir.resolve("models/gemma-2b-it-int4.bin")

    override fun isModelAvailable(): Boolean = modelFile.exists()

    private fun ensureLoaded() {
        if (inference != null) return
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(512)
            .setTopK(40)
            .setTemperature(0.7f)
            .build()
        inference = LlmInference.createFromOptions(context, options)
    }

    override suspend fun summarize(text: String): String = withContext(Dispatchers.Default) {
        ensureLoaded()
        val prompt = """Summarize this article in 3-4 sentences for a podcast listener:

$text

Summary:"""
        inference!!.generateResponse(prompt).trim()
    }
}
```

ModelDownloadManager.kt:
```kotlin
package com.frybynite.podcastapp.deepdive

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class ModelDownloadState {
    data object Idle : ModelDownloadState()
    data class Downloading(val progress: Float) : ModelDownloadState()
    data object Complete : ModelDownloadState()
    data class Failed(val error: String) : ModelDownloadState()
}

@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient
) {
    companion object {
        const val MODEL_URL = "https://storage.googleapis.com/mediapipe-models/llm_inference/gemma-2b-it-gpu-int4/float16/latest/model.task"
    }

    private val _state = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val state: StateFlow<ModelDownloadState> = _state

    suspend fun downloadModel() = withContext(Dispatchers.IO) {
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
        }.onFailure { e ->
            _state.value = ModelDownloadState.Failed(e.message ?: "Download failed")
        }
    }
}
```

TDD order: write TextSummarizerContractTest → verify fail → create TextSummarizer → verify pass → create GemmaTextSummarizer + ModelDownloadManager → assembleDebug → commit.

NOTE: If LlmInference import path changed in 0.10.22, adapt. Key API: LlmInference.createFromOptions(), LlmInferenceOptions.builder(), generateResponse(prompt).

Commit message: `feat: TextSummarizer interface, GemmaTextSummarizer, ModelDownloadManager`

---

### Task 5: TtsSynthesizer

**File:** `app/src/main/kotlin/com/frybynite/podcastapp/deepdive/TtsSynthesizer.kt`

No unit test (requires Android TTS engine on device).

```kotlin
package com.frybynite.podcastapp.deepdive

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class TtsSynthesizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null
    private var ready = false

    private suspend fun ensureReady() = suspendCancellableCoroutine<Unit> { cont ->
        if (ready) { cont.resume(Unit); return@suspendCancellableCoroutine }
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) { ready = true; cont.resume(Unit) }
            else cont.resumeWithException(IllegalStateException("TTS init failed: $status"))
        }
    }

    suspend fun synthesizeToFile(text: String): File {
        ensureReady()
        val file = File(context.cacheDir, "tts_${UUID.randomUUID()}.wav")
        suspendCancellableCoroutine<Unit> { cont ->
            val id = UUID.randomUUID().toString()
            tts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {}
                override fun onDone(utteranceId: String) { if (utteranceId == id) cont.resume(Unit) }
                @Deprecated("Deprecated in API 21")
                override fun onError(utteranceId: String) {
                    cont.resumeWithException(RuntimeException("TTS synthesis failed"))
                }
            })
            tts!!.synthesizeToFile(text, Bundle(), file, id)
        }
        return file
    }

    fun release() { tts?.stop(); tts?.shutdown(); tts = null; ready = false }
}
```

Verify: `./gradlew :app:assembleDebug`
Commit: `feat: TtsSynthesizer — Android TTS synthesizeToFile wrapper`

---

### Task 6: DeepDiveOrchestrator

**Files:**
- Create: `app/src/main/kotlin/com/frybynite/podcastapp/deepdive/DeepDiveOrchestrator.kt`
- Test: `app/src/test/kotlin/com/frybynite/podcastapp/deepdive/DeepDiveOrchestratorTest.kt`

DeepDiveOrchestratorTest.kt (write first, verify fails):
```kotlin
package com.frybynite.podcastapp.deepdive

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class DeepDiveOrchestratorTest {
    private val fetcher = mockk<UrlContentFetcher>()
    private val summarizer = mockk<TextSummarizer>()
    private val tts = mockk<TtsSynthesizer>()
    private val orchestrator = DeepDiveOrchestrator(fetcher, summarizer, tts)

    @Test fun `process fetches, summarizes, then synthesizes`() = runTest {
        every { fetcher.fetch("https://example.com") } returns "Article text content"
        coEvery { summarizer.summarize("Article text content") } returns "Short summary."
        val fakeFile = mockk<File>()
        coEvery { tts.synthesizeToFile("Short summary.") } returns fakeFile

        val result = orchestrator.process("https://example.com")

        assertEquals(fakeFile, result)
        coVerify(exactly = 1) { summarizer.summarize("Article text content") }
        coVerify(exactly = 1) { tts.synthesizeToFile("Short summary.") }
    }
}
```

DeepDiveOrchestrator.kt:
```kotlin
package com.frybynite.podcastapp.deepdive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class DeepDiveOrchestrator @Inject constructor(
    private val fetcher: UrlContentFetcher,
    private val summarizer: TextSummarizer,
    private val tts: TtsSynthesizer
) {
    suspend fun process(url: String): File = withContext(Dispatchers.IO) {
        val text = fetcher.fetch(url)
        val summary = summarizer.summarize(text)
        tts.synthesizeToFile(summary)
    }
}
```

Note: mockk may need `kotlinx-coroutines-test` dep. Check if present; add if not:
`testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")`

Commit: `feat: DeepDiveOrchestrator — fetch → summarize → TTS pipeline`

---

### Task 7: Hilt module

**File:** `app/src/main/kotlin/com/frybynite/podcastapp/deepdive/DeepDiveModule.kt`

```kotlin
package com.frybynite.podcastapp.deepdive

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DeepDiveModule {
    @Binds @Singleton
    abstract fun bindTextSummarizer(impl: GemmaTextSummarizer): TextSummarizer
}
```

Verify: `./gradlew :app:assembleDebug`
Commit: `feat: Hilt module binding TextSummarizer to GemmaTextSummarizer`

---

### Task 8: Fix PlaybackService.onAddMediaItems

**File:** `app/src/main/kotlin/com/frybynite/podcastapp/service/PlaybackService.kt`

Current onAddMediaItems blindly sets uri = mediaId. TTS items have a local file URI already set. Fix to preserve it:

```kotlin
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

Verify: `./gradlew :app:assembleDebug`
Commit: `fix: preserve existing URI in onAddMediaItems for local file items`

---

### Task 9: PlayerViewModel — DeepDiveState + moreAboutThis() + audio injection

**File:** `app/src/main/kotlin/com/frybynite/podcastapp/ui/player/PlayerViewModel.kt`

Add to constructor params:
```kotlin
private val deepDiveOrchestrator: DeepDiveOrchestrator,
private val summarizer: TextSummarizer,
private val modelDownloadManager: ModelDownloadManager,
```

Add state flows (inside class):
```kotlin
private val _deepDiveState = MutableStateFlow<DeepDiveState>(DeepDiveState.Idle)
val deepDiveState: StateFlow<DeepDiveState> = _deepDiveState.asStateFlow()
val modelDownloadState = modelDownloadManager.state
private var pendingTtsFile: java.io.File? = null
```

Add functions:
```kotlin
fun moreAboutThis() {
    val chapter = _chapters.value.getOrNull(_currentChapterIndex.value) ?: return
    val url = chapter.url ?: run {
        _deepDiveState.value = DeepDiveState.Error("No link for this segment")
        return
    }
    if (!summarizer.isModelAvailable()) {
        _deepDiveState.value = DeepDiveState.ModelRequired
        return
    }
    val savedPositionMs = controller?.currentPosition ?: return
    val episodeUri = controller?.currentMediaItem?.mediaId ?: return

    _deepDiveState.value = DeepDiveState.Loading
    controller?.pause()

    viewModelScope.launch {
        runCatching {
            val ttsFile = deepDiveOrchestrator.process(url)
            pendingTtsFile = ttsFile

            val ttsItem = androidx.media3.common.MediaItem.Builder()
                .setMediaId("tts://${ttsFile.name}")
                .setUri(android.net.Uri.fromFile(ttsFile))
                .build()
            val resumeItem = androidx.media3.common.MediaItem.Builder()
                .setMediaId(episodeUri)
                .setUri(episodeUri)
                .setClippingConfiguration(
                    androidx.media3.common.MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(savedPositionMs)
                        .build()
                )
                .build()

            controller?.setMediaItems(listOf(ttsItem, resumeItem))
            controller?.prepare()
            controller?.play()
            _deepDiveState.value = DeepDiveState.Playing
        }.onFailure { e ->
            _deepDiveState.value = DeepDiveState.Error(e.message ?: "Deep dive failed")
            controller?.play()
        }
    }
}

fun downloadModel() { viewModelScope.launch { modelDownloadManager.downloadModel() } }

fun dismissDeepDiveError() { _deepDiveState.value = DeepDiveState.Idle }
```

Add to the Player.Listener block inside connect():
```kotlin
override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
    val incomingId = mediaItem?.mediaId ?: return
    if (!incomingId.startsWith("tts://") && _deepDiveState.value == DeepDiveState.Playing) {
        pendingTtsFile?.delete()
        pendingTtsFile = null
        _deepDiveState.value = DeepDiveState.Idle
    }
    updateCurrentChapterIndex()
}
```

Replace onCleared():
```kotlin
override fun onCleared() {
    pendingTtsFile?.delete()
    controller?.release()
    super.onCleared()
}
```

Add at bottom of file (outside class):
```kotlin
sealed class DeepDiveState {
    data object Idle : DeepDiveState()
    data object ModelRequired : DeepDiveState()
    data object Loading : DeepDiveState()
    data object Playing : DeepDiveState()
    data class Error(val message: String) : DeepDiveState()
}
```

Verify: `./gradlew :app:assembleDebug`
Commit: `feat: PlayerViewModel deep dive — moreAboutThis(), audio injection, TTS cleanup`

---

### Task 10: PlayerScreen wire-up

**File:** `app/src/main/kotlin/com/frybynite/podcastapp/ui/player/PlayerScreen.kt`

1. Add state collection:
```kotlin
val deepDiveState by vm.deepDiveState.collectAsStateWithLifecycle()
val modelDownloadState by vm.modelDownloadState.collectAsStateWithLifecycle()
```

2. In voiceLauncher when block, replace the no-op stub for MORE_ABOUT_THIS:
```kotlin
VoiceCommand.MORE_ABOUT_THIS -> vm.moreAboutThis()
```

3. Add after showSpeedSheet block:
```kotlin
if (deepDiveState is DeepDiveState.ModelRequired) {
    AlertDialog(
        onDismissRequest = { vm.dismissDeepDiveError() },
        title = { Text("Download AI Model") },
        text = { Text("\"More about this\" requires a ~1.3 GB on-device AI model. Download over Wi-Fi?") },
        confirmButton = {
            TextButton(onClick = { vm.downloadModel(); vm.dismissDeepDiveError() }) { Text("Download") }
        },
        dismissButton = {
            TextButton(onClick = { vm.dismissDeepDiveError() }) { Text("Cancel") }
        }
    )
}
```

4. Inside Scaffold content (after main content):
```kotlin
if (deepDiveState is DeepDiveState.Loading) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator()
            Text("Generating deep dive…")
        }
    }
}

if (deepDiveState is DeepDiveState.Error) {
    LaunchedEffect(deepDiveState) {
        delay(3000)
        vm.dismissDeepDiveError()
    }
    Box(Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.BottomCenter) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
            Text(
                text = (deepDiveState as DeepDiveState.Error).message,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

if (modelDownloadState is ModelDownloadState.Downloading) {
    val progress = (modelDownloadState as ModelDownloadState.Downloading).progress
    Box(Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.BottomCenter) {
        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 4.dp) {
            Column(Modifier.padding(16.dp).fillMaxWidth(0.8f)) {
                Text("Downloading model: ${(progress * 100).toInt()}%")
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
```

Verify: `./gradlew :app:assembleDebug`
Commit: `feat: wire MORE_ABOUT_THIS voice command and deep dive UI in PlayerScreen`

---

### Task 11: Full test suite + final build

```bash
./gradlew :app:test 2>&1 | tail -20
./gradlew :app:assembleDebug 2>&1 | tail -5
```

Both must succeed. Fix any failures. Final commit if needed:
`feat: more about this — on-device deep dive with Gemma + Android TTS`
