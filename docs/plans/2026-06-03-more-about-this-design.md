# "More About This" Deep-Dive Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Voice command "more about this" mid-playback fetches, summarizes (on-device Gemma), and injects a TTS audio segment when the current chapter has a URL.

**Architecture:** On voice trigger, `DeepDiveOrchestrator` fetches the chapter URL, extracts text (Jsoup), summarizes via MediaPipe LLM Inference (Gemma 2B INT4), synthesizes speech via Android TTS to a temp WAV file, then injects that file as a `MediaItem` ahead of a clipped-resume `MediaItem` in ExoPlayer's queue. Skipping the TTS item automatically advances to the resume item; `onMediaItemTransition` cleans up the temp file.

**Tech Stack:** MediaPipe tasks-genai (Gemma 2B INT4), Android TextToSpeech, OkHttp (existing), Jsoup, ExoPlayer `ClippingConfiguration`, Hilt, Kotlin coroutines.

---

### Task 1: Add dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Step 1: Add version catalog entries**

In `gradle/libs.versions.toml`, add under `[versions]` and `[libraries]`:
```toml
[versions]
# add:
mediapipe-tasks-genai = "0.10.22"
jsoup = "1.17.2"

[libraries]
# add:
mediapipe-tasks-genai = { group = "com.google.mediapipe", name = "tasks-genai", version.ref = "mediapipe-tasks-genai" }
jsoup = { group = "org.jsoup", name = "jsoup", version.ref = "jsoup" }
```

**Step 2: Add to app/build.gradle.kts**

```kotlin
implementation(libs.mediapipe.tasks.genai)
implementation(libs.jsoup)
```

Also add mockk for tests if not present:
```kotlin
testImplementation("io.mockk:mockk:1.13.10")
```

**Step 3: Sync and verify build**

Run: `./gradlew :app:dependencies | grep -E "mediapipe|jsoup"`
Expected: both libraries listed

**Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add MediaPipe tasks-genai, Jsoup, and mockk dependencies"
```

---

### Task 2: Add VoiceCommand.MORE_ABOUT_THIS

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/ui/player/VoiceCommandHandler.kt`
- Test: `app/src/test/kotlin/com/frybynite/podcastapp/ui/player/VoiceCommandHandlerTest.kt`

**Step 1: Write failing tests**

Create `app/src/test/kotlin/com/frybynite/podcastapp/ui/player/VoiceCommandHandlerTest.kt`:

```kotlin
package com.frybynite.podcastapp.ui.player

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VoiceCommandHandlerTest {
    @Test fun `parse more about this`() = assertEquals(VoiceCommand.MORE_ABOUT_THIS, VoiceCommandHandler.parse("more about this"))
    @Test fun `parse tell me more`() = assertEquals(VoiceCommand.MORE_ABOUT_THIS, VoiceCommandHandler.parse("tell me more"))
    @Test fun `parse more detail`() = assertEquals(VoiceCommand.MORE_ABOUT_THIS, VoiceCommandHandler.parse("more detail"))
    @Test fun `parse explain this`() = assertEquals(VoiceCommand.MORE_ABOUT_THIS, VoiceCommandHandler.parse("explain this"))
    @Test fun `parse learn more`() = assertEquals(VoiceCommand.MORE_ABOUT_THIS, VoiceCommandHandler.parse("learn more"))
    @Test fun `parse deep dive`() = assertEquals(VoiceCommand.MORE_ABOUT_THIS, VoiceCommandHandler.parse("deep dive"))
    @Test fun `parse unknown returns null`() = assertNull(VoiceCommandHandler.parse("hello there"))
    @Test fun `parse is case insensitive`() = assertEquals(VoiceCommand.MORE_ABOUT_THIS, VoiceCommandHandler.parse("More About This"))
}
```

**Step 2: Run to verify fails**

Run: `./gradlew :app:test --tests "*.VoiceCommandHandlerTest" 2>&1 | tail -20`
Expected: FAILED — `MORE_ABOUT_THIS` not found in enum

**Step 3: Implement**

Replace `VoiceCommandHandler.kt` with:

```kotlin
package com.frybynite.podcastapp.ui.player

enum class VoiceCommand {
    NEXT_CHAPTER, PREV_CHAPTER, SEEK_FORWARD, SEEK_BACK,
    OPEN_LINK, SHARE_LINK, MORE_ABOUT_THIS
}

object VoiceCommandHandler {
    fun parse(input: String): VoiceCommand? = when (input.trim().lowercase()) {
        "next", "next section", "skip", "forward" -> VoiceCommand.NEXT_CHAPTER
        "back", "previous", "previous section", "go back" -> VoiceCommand.PREV_CHAPTER
        "fast forward", "skip forward" -> VoiceCommand.SEEK_FORWARD
        "rewind", "skip back" -> VoiceCommand.SEEK_BACK
        "open link", "open", "open article" -> VoiceCommand.OPEN_LINK
        "save link", "save", "add to list", "share link" -> VoiceCommand.SHARE_LINK
        "more about this", "tell me more", "more detail",
        "explain this", "learn more", "deep dive" -> VoiceCommand.MORE_ABOUT_THIS
        else -> null
    }
}
```

**Step 4: Run to verify passes**

Run: `./gradlew :app:test --tests "*.VoiceCommandHandlerTest"`
Expected: BUILD SUCCESSFUL, 8 tests passed

**Step 5: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/ui/player/VoiceCommandHandler.kt \
        app/src/test/kotlin/com/frybynite/podcastapp/ui/player/VoiceCommandHandlerTest.kt
git commit -m "feat: add VoiceCommand.MORE_ABOUT_THIS with phrase matching"
```

---

### Task 3: UrlContentFetcher

**Files:**
- Create: `app/src/main/kotlin/com/frybynite/podcastapp/deepdive/UrlContentFetcher.kt`
- Test: `app/src/test/kotlin/com/frybynite/podcastapp/deepdive/UrlContentFetcherTest.kt`

**Step 1: Write failing tests**

`MockWebServer` is already in test deps (`okhttp.mockwebserver`).

Create `app/src/test/kotlin/com/frybynite/podcastapp/deepdive/UrlContentFetcherTest.kt`:

```kotlin
package com.frybynite.podcastapp.deepdive

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UrlContentFetcherTest {
    private val server = MockWebServer()
    private lateinit var fetcher: UrlContentFetcher

    @Before fun setUp() {
        server.start()
        fetcher = UrlContentFetcher(OkHttpClient())
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun `extracts article text, excludes nav`() {
        server.enqueue(MockResponse().setBody("""
            <html><body>
              <article><p>Main content here.</p><p>Second paragraph.</p></article>
              <nav>Skip nav</nav>
            </body></html>
        """.trimIndent()).addHeader("Content-Type", "text/html"))
        val result = fetcher.fetch(server.url("/").toString())
        assertTrue(result.contains("Main content here."))
        assertFalse(result.contains("Skip nav"))
    }

    @Test fun `truncates to 3000 chars`() {
        val longText = "word ".repeat(1000)
        server.enqueue(MockResponse()
            .setBody("<html><body><article><p>$longText</p></article></body></html>")
            .addHeader("Content-Type", "text/html"))
        val result = fetcher.fetch(server.url("/").toString())
        assertTrue(result.length <= 3000)
    }

    @Test fun `falls back to body when no article tag`() {
        server.enqueue(MockResponse().setBody("""
            <html><body><p>Only body content.</p></body></html>
        """.trimIndent()).addHeader("Content-Type", "text/html"))
        val result = fetcher.fetch(server.url("/").toString())
        assertTrue(result.contains("Only body content."))
    }
}
```

**Step 2: Run to verify fails**

Run: `./gradlew :app:test --tests "*.UrlContentFetcherTest" 2>&1 | tail -20`
Expected: FAILED — class not found

**Step 3: Implement**

Create `app/src/main/kotlin/com/frybynite/podcastapp/deepdive/UrlContentFetcher.kt`:

```kotlin
package com.frybynite.podcastapp.deepdive

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import javax.inject.Inject

class UrlContentFetcher @Inject constructor(private val client: OkHttpClient) {

    fun fetch(url: String): String {
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        val html = response.body?.string() ?: return ""
        val doc = Jsoup.parse(html)
        val text = (doc.selectFirst("article")
            ?: doc.selectFirst("main")
            ?: doc.body())
            ?.text() ?: ""
        return text.take(3000)
    }
}
```

**Step 4: Run to verify passes**

Run: `./gradlew :app:test --tests "*.UrlContentFetcherTest"`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/deepdive/UrlContentFetcher.kt \
        app/src/test/kotlin/com/frybynite/podcastapp/deepdive/UrlContentFetcherTest.kt
git commit -m "feat: add UrlContentFetcher — fetch and extract article text via Jsoup"
```

---

### Task 4: TextSummarizer interface + GemmaTextSummarizer + ModelDownloadManager

**Files:**
- Create: `app/src/main/kotlin/com/frybynite/podcastapp/deepdive/TextSummarizer.kt`
- Create: `app/src/main/kotlin/com/frybynite/podcastapp/deepdive/GemmaTextSummarizer.kt`
- Create: `app/src/main/kotlin/com/frybynite/podcastapp/deepdive/ModelDownloadManager.kt`
- Test: `app/src/test/kotlin/com/frybynite/podcastapp/deepdive/TextSummarizerContractTest.kt`

**Step 1: Write failing test (interface contract)**

Create `app/src/test/kotlin/com/frybynite/podcastapp/deepdive/TextSummarizerContractTest.kt`:

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

Run: `./gradlew :app:test --tests "*.TextSummarizerContractTest" 2>&1 | tail -10`
Expected: FAILED — interface not found

**Step 2: Implement TextSummarizer interface**

Create `app/src/main/kotlin/com/frybynite/podcastapp/deepdive/TextSummarizer.kt`:

```kotlin
package com.frybynite.podcastapp.deepdive

interface TextSummarizer {
    fun isModelAvailable(): Boolean
    suspend fun summarize(text: String): String
}
```

**Step 3: Run interface test**

Run: `./gradlew :app:test --tests "*.TextSummarizerContractTest"`
Expected: BUILD SUCCESSFUL

**Step 4: Implement GemmaTextSummarizer**

Create `app/src/main/kotlin/com/frybynite/podcastapp/deepdive/GemmaTextSummarizer.kt`:

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

**Step 5: Implement ModelDownloadManager**

Create `app/src/main/kotlin/com/frybynite/podcastapp/deepdive/ModelDownloadManager.kt`:

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
    // Gemma 2B IT INT4 in MediaPipe .task format.
    // Verify current URL at: https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android
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

**Step 6: Verify build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/deepdive/ \
        app/src/test/kotlin/com/frybynite/podcastapp/deepdive/TextSummarizerContractTest.kt
git commit -m "feat: TextSummarizer interface, GemmaTextSummarizer, ModelDownloadManager"
```

---

### Task 5: TtsSynthesizer

**Files:**
- Create: `app/src/main/kotlin/com/frybynite/podcastapp/deepdive/TtsSynthesizer.kt`

No unit test — Android TTS engine requires device/emulator. Covered by end-to-end test in Task 11.

**Step 1: Implement**

Create `app/src/main/kotlin/com/frybynite/podcastapp/deepdive/TtsSynthesizer.kt`:

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

**Step 2: Verify build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/deepdive/TtsSynthesizer.kt
git commit -m "feat: TtsSynthesizer — Android TTS synthesizeToFile wrapper"
```

---

### Task 6: DeepDiveOrchestrator

**Files:**
- Create: `app/src/main/kotlin/com/frybynite/podcastapp/deepdive/DeepDiveOrchestrator.kt`
- Test: `app/src/test/kotlin/com/frybynite/podcastapp/deepdive/DeepDiveOrchestratorTest.kt`

**Step 1: Write failing test**

Create `app/src/test/kotlin/com/frybynite/podcastapp/deepdive/DeepDiveOrchestratorTest.kt`:

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

**Step 2: Run to verify fails**

Run: `./gradlew :app:test --tests "*.DeepDiveOrchestratorTest" 2>&1 | tail -10`
Expected: FAILED — class not found

**Step 3: Implement**

Create `app/src/main/kotlin/com/frybynite/podcastapp/deepdive/DeepDiveOrchestrator.kt`:

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

**Step 4: Run to verify passes**

Run: `./gradlew :app:test --tests "*.DeepDiveOrchestratorTest"`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/deepdive/DeepDiveOrchestrator.kt \
        app/src/test/kotlin/com/frybynite/podcastapp/deepdive/DeepDiveOrchestratorTest.kt
git commit -m "feat: DeepDiveOrchestrator — fetch → summarize → TTS pipeline"
```

---

### Task 7: Hilt module

**Files:**
- Create: `app/src/main/kotlin/com/frybynite/podcastapp/deepdive/DeepDiveModule.kt`

**Step 1: Implement**

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

**Step 2: Verify build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/deepdive/DeepDiveModule.kt
git commit -m "feat: Hilt module binding TextSummarizer to GemmaTextSummarizer"
```

---

### Task 8: Fix PlaybackService.onAddMediaItems for local file URIs

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/service/PlaybackService.kt`

The current callback does `setUri(item.mediaId)` for every item. TTS items have `mediaId = "tts://<uuid>"` but the actual URI is a local `file://` path already set via `setUri()`. This fix preserves pre-set URIs.

**Step 1: Locate the callback**

Find `onAddMediaItems` — currently around lines 143–150:
```kotlin
override fun onAddMediaItems(...): ListenableFuture<List<MediaItem>> =
    Futures.immediateFuture(mediaItems.map { item ->
        item.buildUpon().setUri(item.mediaId).build()
    })
```

**Step 2: Replace with URI-preserving version**

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

**Step 3: Verify build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/service/PlaybackService.kt
git commit -m "fix: preserve existing URI in onAddMediaItems for local file items"
```

---

### Task 9: PlayerViewModel — DeepDiveState + moreAboutThis() + audio injection

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/ui/player/PlayerViewModel.kt`

**Step 1: Add DeepDiveState sealed class**

Add at the bottom of `PlayerViewModel.kt` (outside the class):

```kotlin
sealed class DeepDiveState {
    data object Idle : DeepDiveState()
    data object ModelRequired : DeepDiveState()
    data object Loading : DeepDiveState()
    data object Playing : DeepDiveState()
    data class Error(val message: String) : DeepDiveState()
}
```

**Step 2: Inject deep dive dependencies**

Add to `@HiltViewModel class PlayerViewModel @Inject constructor(...)`:
```kotlin
private val deepDiveOrchestrator: DeepDiveOrchestrator,
private val summarizer: TextSummarizer,
private val modelDownloadManager: ModelDownloadManager,
```

**Step 3: Add state flows and pending file**

```kotlin
private val _deepDiveState = MutableStateFlow<DeepDiveState>(DeepDiveState.Idle)
val deepDiveState: StateFlow<DeepDiveState> = _deepDiveState.asStateFlow()

val modelDownloadState = modelDownloadManager.state

private var pendingTtsFile: java.io.File? = null
```

**Step 4: Add moreAboutThis(), downloadModel(), dismissDeepDiveError()**

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

**Step 5: Add TTS cleanup listener in connect()**

Inside the `controller?.addListener(object : Player.Listener { ... })` block in `connect()`, add:

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

**Step 6: Clean up in onCleared()**

Replace existing `onCleared()`:
```kotlin
override fun onCleared() {
    pendingTtsFile?.delete()
    controller?.release()
    super.onCleared()
}
```

**Step 7: Verify build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 8: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/ui/player/PlayerViewModel.kt
git commit -m "feat: PlayerViewModel deep dive — moreAboutThis(), audio injection, TTS cleanup"
```

---

### Task 10: PlayerScreen — wire voice command + UI states

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/ui/player/PlayerScreen.kt`

**Step 1: Collect new state flows**

In `PlayerScreen`, add alongside existing state collections:

```kotlin
val deepDiveState by vm.deepDiveState.collectAsStateWithLifecycle()
val modelDownloadState by vm.modelDownloadState.collectAsStateWithLifecycle()
```

**Step 2: Handle MORE_ABOUT_THIS in voiceLauncher**

In the `voiceLauncher` result handler `when` block, add:

```kotlin
VoiceCommand.MORE_ABOUT_THIS -> vm.moreAboutThis()
```

**Step 3: Add deep dive UI overlays**

After the existing `if (showSpeedSheet)` block, add:

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

After the `Scaffold { ... }` closing brace (so it overlays), wrap in a `Box`:

Actually, place these inside the `Scaffold` content lambda, after the main content:

```kotlin
// Loading overlay
if (deepDiveState is DeepDiveState.Loading) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator()
            Text("Generating deep dive…")
        }
    }
}

// Error toast
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

// Model download progress
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

**Step 4: Verify build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/ui/player/PlayerScreen.kt
git commit -m "feat: wire MORE_ABOUT_THIS voice command and deep dive UI in PlayerScreen"
```

---

### Task 11: Full test suite + final check

**Step 1: Run all unit tests**

Run: `./gradlew :app:test 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL, no failures

**Step 2: Build debug APK**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit any fixups**

```bash
git add -A
git commit -m "feat: more about this — on-device deep dive with Gemma + Android TTS"
```

---

## Notes

- **Gemma model URL**: `MODEL_URL` in `ModelDownloadManager` — verify it's current before shipping. See https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android.
- **Minimum API**: MediaPipe LLM Inference requires Android 7+ (API 24). Gemma INT4 runs on CPU; GPU acceleration available on compatible hardware.
- **Model size ~1.3 GB**: Enforce Wi-Fi-only download in the dialog. For a production build, migrate `ModelDownloadManager` to `WorkManager` so the download survives process death.
- **Skip behavior**: "skip" during TTS playback → `nextChapter()` → `CMD_NEXT_CHAPTER` → `player.seekToNextMediaItem()` → advances to clipped resume item → `onMediaItemTransition` fires → temp file deleted.
- **Cold start**: `GemmaTextSummarizer` loads the model lazily on first `summarize()` call. First inference will be slow (~10–30s on mid-range hardware) after model load.
