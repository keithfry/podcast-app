# Content Caching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist downloaded audio, the per-episode metadata JSON, and generated "More about this" deep dives into a per-podcast/per-episode directory hierarchy, and reuse them instead of re-downloading or re-generating.

**Architecture:** A new `CacheStorage` owns all path math (human-readable `slug-hash8` directory names under `filesDir/podcasts/`). A new `deep_dives` Room table records generated TTS segments keyed by `(episodeAudioUrl, chapterUrl)`. `DownloadWorker` and `DeepDiveOrchestrator` write into the hierarchy; the orchestrator checks the table/metadata file before doing any network or generation work.

**Tech Stack:** Kotlin, Room, Hilt, WorkManager, OkHttp, JUnit + mockk + kotlinx-coroutines-test.

**Spec:** `docs/plans/2026-06-04-content-caching-design.md`

**Note — already done:** Refresh-on-open (requirement 1) is already implemented: `EpisodeListScreen` calls `vm.refresh()` in a `LaunchedEffect(Unit)`, which calls `repo.refreshPodcast(feedUrl)`. No task needed; verify it still works after these changes.

---

## File Structure

- Create: `app/src/main/kotlin/com/frybynite/podlore/data/storage/CacheStorage.kt` — path math + slug/hash helpers.
- Create: `app/src/main/kotlin/com/frybynite/podlore/data/db/entities/DeepDiveEntity.kt` — deep-dive cache row.
- Create: `app/src/main/kotlin/com/frybynite/podlore/data/db/dao/DeepDiveDao.kt` — lookup/upsert.
- Create: `app/src/test/kotlin/com/frybynite/podlore/data/storage/CacheStorageTest.kt`
- Modify: `app/src/main/kotlin/com/frybynite/podlore/data/db/PodcastDatabase.kt` — add entity, bump version, migration.
- Modify: `app/src/main/kotlin/com/frybynite/podlore/data/di/DatabaseModule.kt` — provide `DeepDiveDao`, add migration, provide podcasts dir `File`.
- Modify: `app/src/main/kotlin/com/frybynite/podlore/data/download/DownloadWorker.kt` — write into hierarchy.
- Modify: `app/src/main/kotlin/com/frybynite/podlore/deepdive/DeepDiveOrchestrator.kt` — cache check + persist + metadata.json caching.
- Modify: `app/src/test/kotlin/com/frybynite/podlore/deepdive/DeepDiveOrchestratorTest.kt` — new deps + cache-hit test.
- Modify: `app/src/main/kotlin/com/frybynite/podlore/ui/player/PlayerViewModel.kt` — pass chapter title, stop deleting cached files.

---

### Task 1: CacheStorage path helper

**Files:**
- Create: `app/src/main/kotlin/com/frybynite/podlore/data/storage/CacheStorage.kt`
- Test: `app/src/test/kotlin/com/frybynite/podlore/data/storage/CacheStorageTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.frybynite.podlore.data.storage

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CacheStorageTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun storage() = CacheStorage(tmp.newFolder("podcasts"))

    @Test fun `slug lowercases and replaces punctuation`() {
        val dir = storage().podcastDir("https://feed.example/rss", "AI & Robotics Daily!")
        assertTrue(dir.name.startsWith("ai-robotics-daily-"))
    }

    @Test fun `empty title slugs to untitled`() {
        val dir = storage().podcastDir("https://feed.example/rss", "   ")
        assertTrue(dir.name.startsWith("untitled-"))
    }

    @Test fun `same title different url produce different dirs`() {
        val s = storage()
        val a = s.podcastDir("https://a.example/rss", "Show")
        val b = s.podcastDir("https://b.example/rss", "Show")
        assertTrue(a.name != b.name)
    }

    @Test fun `hash is deterministic`() {
        val a = storage().podcastDir("https://a.example/rss", "Show").name
        val b = storage().podcastDir("https://a.example/rss", "Show").name
        assertEquals(a, b)
    }

    @Test fun `main audio file keeps extension and nests under episode dir`() {
        val f = storage().mainAudioFile("https://f/rss", "Show", "https://cdn/ep1.m4a", "Ep 1")
        assertEquals("audio.m4a", f.name)
        assertTrue(f.parentFile!!.name.startsWith("ep-1-"))
    }

    @Test fun `main audio defaults to mp3 when url has no extension`() {
        val f = storage().mainAudioFile("https://f/rss", "Show", "https://cdn/stream?id=9", "Ep 1")
        assertEquals("audio.mp3", f.name)
    }

    @Test fun `metadata file is metadata json`() {
        val f = storage().metadataFile("https://f/rss", "Show", "https://cdn/ep1.mp3", "Ep 1")
        assertEquals("metadata.json", f.name)
    }

    @Test fun `deep dive file uses chapter slug and wav extension`() {
        val f = storage().deepDiveFile("https://f/rss", "Show", "https://cdn/ep1.mp3", "Ep 1",
            "https://news/x", "Courts & AI Lawsuits")
        assertTrue(f.name.startsWith("more-courts-ai-lawsuits-"))
        assertTrue(f.name.endsWith(".wav"))
    }

    @Test fun `deep dive file falls back to url slug when title null`() {
        val f = storage().deepDiveFile("https://f/rss", "Show", "https://cdn/ep1.mp3", "Ep 1",
            "https://news/x", null)
        assertTrue(f.name.startsWith("more-"))
        assertTrue(f.name.endsWith(".wav"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.frybynite.podlore.data.storage.CacheStorageTest"`
Expected: FAIL — `CacheStorage` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.frybynite.podlore.data.storage

import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Named

class CacheStorage @Inject constructor(
    @Named("podcasts_dir") private val root: File
) {
    fun podcastDir(feedUrl: String, podcastTitle: String): File =
        File(root, "${slug(podcastTitle)}-${hash8(feedUrl)}")

    fun episodeDir(feedUrl: String, podcastTitle: String, audioUrl: String, episodeTitle: String): File =
        File(podcastDir(feedUrl, podcastTitle), "${slug(episodeTitle)}-${hash8(audioUrl)}")

    fun mainAudioFile(feedUrl: String, podcastTitle: String, audioUrl: String, episodeTitle: String): File =
        File(episodeDir(feedUrl, podcastTitle, audioUrl, episodeTitle), "audio.${ext(audioUrl)}")

    fun metadataFile(feedUrl: String, podcastTitle: String, audioUrl: String, episodeTitle: String): File =
        File(episodeDir(feedUrl, podcastTitle, audioUrl, episodeTitle), "metadata.json")

    fun deepDiveFile(
        feedUrl: String, podcastTitle: String, audioUrl: String, episodeTitle: String,
        chapterUrl: String, chapterTitle: String?
    ): File = File(
        episodeDir(feedUrl, podcastTitle, audioUrl, episodeTitle),
        "more-${slug(chapterTitle ?: chapterUrl)}-${hash8(chapterUrl)}.wav"
    )

    private fun slug(text: String): String {
        val s = text.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
            .trim('-')
        return s.ifEmpty { "untitled" }
    }

    private fun ext(url: String): String {
        val tail = url.substringBefore('?').substringBefore('#').substringAfterLast('.', "")
        return tail.lowercase().takeIf { it.isNotEmpty() && it.length <= 4 } ?: "mp3"
    }

    private fun hash8(url: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(8)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.frybynite.podlore.data.storage.CacheStorageTest"`
Expected: PASS (all 9).

- [ ] **Step 5: Provide the podcasts dir via Hilt**

Modify `app/src/main/kotlin/com/frybynite/podlore/data/di/DatabaseModule.kt` — add inside the `object DatabaseModule`:

```kotlin
    @Provides
    @Singleton
    @javax.inject.Named("podcasts_dir")
    fun providePodcastsDir(@ApplicationContext ctx: Context): java.io.File =
        java.io.File(ctx.filesDir, "podcasts")
```

- [ ] **Step 6: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podlore/data/storage/CacheStorage.kt \
        app/src/test/kotlin/com/frybynite/podlore/data/storage/CacheStorageTest.kt \
        app/src/main/kotlin/com/frybynite/podlore/data/di/DatabaseModule.kt
git commit -m "feat: CacheStorage path helper for hierarchical content cache"
```

---

### Task 2: DeepDive Room table

**Files:**
- Create: `app/src/main/kotlin/com/frybynite/podlore/data/db/entities/DeepDiveEntity.kt`
- Create: `app/src/main/kotlin/com/frybynite/podlore/data/db/dao/DeepDiveDao.kt`
- Modify: `app/src/main/kotlin/com/frybynite/podlore/data/db/PodcastDatabase.kt`
- Modify: `app/src/main/kotlin/com/frybynite/podlore/data/di/DatabaseModule.kt`

- [ ] **Step 1: Create the entity**

```kotlin
package com.frybynite.podlore.data.db.entities

import androidx.room.Entity

@Entity(tableName = "deep_dives", primaryKeys = ["episodeAudioUrl", "chapterUrl"])
data class DeepDiveEntity(
    val episodeAudioUrl: String,
    val chapterUrl: String,
    val filePath: String,
    val summaryText: String,
    val createdAt: Long
)
```

- [ ] **Step 2: Create the DAO**

```kotlin
package com.frybynite.podlore.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.frybynite.podlore.data.db.entities.DeepDiveEntity

@Dao
interface DeepDiveDao {
    @Query("SELECT * FROM deep_dives WHERE episodeAudioUrl = :episodeAudioUrl AND chapterUrl = :chapterUrl")
    suspend fun get(episodeAudioUrl: String, chapterUrl: String): DeepDiveEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DeepDiveEntity)

    @Query("DELETE FROM deep_dives WHERE episodeAudioUrl = :episodeAudioUrl")
    suspend fun deleteForEpisode(episodeAudioUrl: String)
}
```

- [ ] **Step 3: Register entity, bump version, add migration**

In `PodcastDatabase.kt` change the `@Database` annotation and add the DAO accessor:

```kotlin
@Database(
    entities = [PodcastEntity::class, EpisodeEntity::class, ChapterEntity::class, DeepDiveEntity::class],
    version = 2,
    exportSchema = false
)
abstract class PodcastDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun chapterDao(): ChapterDao
    abstract fun deepDiveDao(): DeepDiveDao
}
```

Add the import: `import com.frybynite.podlore.data.db.entities.DeepDiveEntity` and `import com.frybynite.podlore.data.db.dao.DeepDiveDao`.

- [ ] **Step 4: Wire migration + DAO provider in DatabaseModule**

In `DatabaseModule.kt`, replace `provideDatabase` and add the DAO provider:

```kotlin
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): PodcastDatabase =
        Room.databaseBuilder(ctx, PodcastDatabase::class.java, "podcast.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideDeepDiveDao(db: PodcastDatabase) = db.deepDiveDao()
```

Add this top-level val in the same file (outside the object), with `import androidx.room.migration.Migration` and `import androidx.sqlite.db.SupportSQLiteDatabase`:

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `deep_dives` (" +
                "`episodeAudioUrl` TEXT NOT NULL, " +
                "`chapterUrl` TEXT NOT NULL, " +
                "`filePath` TEXT NOT NULL, " +
                "`summaryText` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`episodeAudioUrl`, `chapterUrl`))"
        )
    }
}
```

- [ ] **Step 5: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (Room generates the new DAO impl; the `CREATE TABLE` SQL matches the entity columns so no migration-validation crash).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podlore/data/db/entities/DeepDiveEntity.kt \
        app/src/main/kotlin/com/frybynite/podlore/data/db/dao/DeepDiveDao.kt \
        app/src/main/kotlin/com/frybynite/podlore/data/db/PodcastDatabase.kt \
        app/src/main/kotlin/com/frybynite/podlore/data/di/DatabaseModule.kt
git commit -m "feat: deep_dives Room table (v2 migration) for cached More-about-this"
```

---

### Task 3: DownloadWorker writes into the hierarchy

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podlore/data/download/DownloadWorker.kt`

- [ ] **Step 1: Inject podcastDao + CacheStorage and use them**

Replace the constructor and `downloadToFile` so the file lands in the episode dir. Full new file:

```kotlin
package com.frybynite.podlore.data.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.frybynite.podlore.data.db.dao.EpisodeDao
import com.frybynite.podlore.data.db.dao.PodcastDao
import com.frybynite.podlore.data.storage.CacheStorage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val episodeDao: EpisodeDao,
    private val podcastDao: PodcastDao,
    private val cacheStorage: CacheStorage,
    private val okHttp: OkHttpClient
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val audioUrl = inputData.getString(KEY_AUDIO_URL) ?: return Result.failure()
        episodeDao.updateDownloadStatus(audioUrl, null, "DOWNLOADING")
        return try {
            val file = downloadToFile(audioUrl)
            episodeDao.updateDownloadStatus(audioUrl, file.absolutePath, "DONE")
            Result.success()
        } catch (e: Exception) {
            episodeDao.updateDownloadStatus(audioUrl, null, "NONE")
            Result.retry()
        }
    }

    private suspend fun downloadToFile(audioUrl: String): File = withContext(Dispatchers.IO) {
        val episode = episodeDao.getByAudioUrl(audioUrl) ?: throw Exception("Unknown episode")
        val podcastTitle = podcastDao.getByUrl(episode.podcastFeedUrl)?.title ?: "untitled"
        val file = cacheStorage.mainAudioFile(
            episode.podcastFeedUrl, podcastTitle, audioUrl, episode.title
        )
        file.parentFile?.mkdirs()

        val request = Request.Builder().url(audioUrl).build()
        val response = okHttp.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        response.body?.byteStream()?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: throw Exception("Empty body")
        file
    }

    companion object {
        const val KEY_AUDIO_URL = "audio_url"

        fun buildRequest(audioUrl: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(KEY_AUDIO_URL to audioUrl))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (Hilt injects `PodcastDao` + `CacheStorage` into the worker).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podlore/data/download/DownloadWorker.kt
git commit -m "feat: downloads write into per-episode cache directory"
```

---

### Task 4: DeepDiveOrchestrator caches metadata + reuses deep dives

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podlore/deepdive/DeepDiveOrchestrator.kt`
- Test: `app/src/test/kotlin/com/frybynite/podlore/deepdive/DeepDiveOrchestratorTest.kt`

- [ ] **Step 1: Update the existing test for the new constructor + add a cache-hit test**

Replace the test file with:

```kotlin
package com.frybynite.podlore.deepdive

import com.frybynite.podlore.data.db.dao.DeepDiveDao
import com.frybynite.podlore.data.db.dao.EpisodeDao
import com.frybynite.podlore.data.db.dao.PodcastDao
import com.frybynite.podlore.data.db.entities.DeepDiveEntity
import com.frybynite.podlore.data.storage.CacheStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals

class DeepDiveOrchestratorTest {
    @get:Rule val tmp = TemporaryFolder()

    private val fetcher = mockk<UrlContentFetcher>()
    private val summarizer = mockk<TextSummarizer>()
    private val tts = mockk<TtsSynthesizer>()
    private val client = mockk<OkHttpClient>(relaxed = true)
    private val deepDiveDao = mockk<DeepDiveDao>(relaxed = true)
    private val episodeDao = mockk<EpisodeDao>(relaxed = true)
    private val podcastDao = mockk<PodcastDao>(relaxed = true)
    private fun storage() = CacheStorage(tmp.newFolder("podcasts"))

    private fun orchestrator(storage: CacheStorage) =
        DeepDiveOrchestrator(fetcher, summarizer, tts, client, storage, deepDiveDao, episodeDao, podcastDao)

    @Test fun `process with no episode generates without caching`() = runTest {
        every { fetcher.fetch("https://example.com") } returns "Article text content"
        coEvery { summarizer.summarize("Article text content", any()) } returns "Short summary."
        val fakeFile = File(tmp.newFolder(), "tts.wav").apply { writeText("x") }
        coEvery { tts.synthesizeToFile("Short summary.") } returns fakeFile

        val result = orchestrator(storage()).process("https://example.com", null)

        assertEquals(fakeFile, result)
        coVerify(exactly = 1) { summarizer.summarize("Article text content", any()) }
    }

    @Test fun `process returns cached file without fetching or synthesizing on hit`() = runTest {
        val cached = File(tmp.newFolder(), "more.wav").apply { writeText("audio") }
        coEvery { deepDiveDao.get("https://cdn/ep1.mp3", "https://news/x") } returns
            DeepDiveEntity("https://cdn/ep1.mp3", "https://news/x", cached.absolutePath, "sum", 1L)

        val result = orchestrator(storage()).process("https://news/x", "https://cdn/ep1.mp3")

        assertEquals(cached.absolutePath, result.absolutePath)
        coVerify(exactly = 0) { fetcher.fetch(any()) }
        coVerify(exactly = 0) { tts.synthesizeToFile(any()) }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.frybynite.podlore.deepdive.DeepDiveOrchestratorTest"`
Expected: FAIL — constructor arity mismatch / `process` signature.

- [ ] **Step 3: Rewrite the orchestrator**

Full new file:

```kotlin
package com.frybynite.podlore.deepdive

import android.util.Log
import com.frybynite.podlore.data.db.dao.DeepDiveDao
import com.frybynite.podlore.data.db.dao.EpisodeDao
import com.frybynite.podlore.data.db.dao.PodcastDao
import com.frybynite.podlore.data.db.entities.DeepDiveEntity
import com.frybynite.podlore.data.storage.CacheStorage
import com.frybynite.podlore.ui.player.DeepDiveStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

class DeepDiveOrchestrator @Inject constructor(
    private val fetcher: UrlContentFetcher,
    private val summarizer: TextSummarizer,
    private val tts: TtsSynthesizer,
    private val client: OkHttpClient,
    private val cacheStorage: CacheStorage,
    private val deepDiveDao: DeepDiveDao,
    private val episodeDao: EpisodeDao,
    private val podcastDao: PodcastDao
) {
    suspend fun process(
        chapterUrl: String,
        episodeAudioUrl: String? = null,
        chapterTitle: String? = null,
        onStep: (DeepDiveStep) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        Log.i("DeepDive", "process: start url=$chapterUrl episode=$episodeAudioUrl")

        // 1. Cache hit: row + file present -> reuse, skip all work. Only needs the episode URL.
        if (episodeAudioUrl != null) {
            val row = deepDiveDao.get(episodeAudioUrl, chapterUrl)
            if (row != null && File(row.filePath).exists()) {
                Log.i("DeepDive", "process: cache HIT ${row.filePath}")
                return@withContext File(row.filePath)
            }
        }

        // Resolve episode + podcast context for cache paths. If unavailable, generate without caching.
        val episode = episodeAudioUrl?.let { episodeDao.getByAudioUrl(it) }
        val podcastTitle = episode?.let { podcastDao.getByUrl(it.podcastFeedUrl)?.title } ?: "untitled"
        val canCache = episode != null && episodeAudioUrl != null

        // 2. Miss: fetch (reusing cached metadata.json for any prior summary).
        onStep(DeepDiveStep.FETCHING)
        val existingSummary = if (canCache)
            fetchExistingSummary(episodeAudioUrl!!, episode!!.podcastFeedUrl, podcastTitle, episode.title, chapterUrl)
        else null
        val text = fetcher.fetch(chapterUrl)
        onStep(DeepDiveStep.SUMMARIZING)
        val summary = summarizer.summarize(text, existingSummary)
        onStep(DeepDiveStep.SYNTHESIZING)
        val tmpFile = tts.synthesizeToFile(summary)

        // 3. If we can cache, move the temp file into the episode dir and record it.
        if (!canCache) {
            Log.i("DeepDive", "process: no episode context, returning temp file (uncached)")
            return@withContext tmpFile
        }
        val target = cacheStorage.deepDiveFile(
            episode!!.podcastFeedUrl, podcastTitle, episodeAudioUrl!!, episode.title, chapterUrl, chapterTitle
        )
        target.parentFile?.mkdirs()
        if (!tmpFile.renameTo(target)) {
            tmpFile.copyTo(target, overwrite = true); tmpFile.delete()
        }
        deepDiveDao.upsert(
            DeepDiveEntity(episodeAudioUrl, chapterUrl, target.absolutePath, summary, System.currentTimeMillis())
        )
        Log.i("DeepDive", "process: cached ${target.absolutePath}")
        target
    }

    /** Reads the cached metadata.json if present; otherwise fetches the .json sidecar once and saves it. */
    private fun fetchExistingSummary(
        episodeAudioUrl: String, feedUrl: String, podcastTitle: String, episodeTitle: String, chapterUrl: String
    ): String? = runCatching {
        val metaFile = cacheStorage.metadataFile(feedUrl, podcastTitle, episodeAudioUrl, episodeTitle)
        val body: String = if (metaFile.exists()) {
            metaFile.readText()
        } else {
            val jsonUrl = episodeAudioUrl.replace(Regex("\\.(mp3|m4a|ogg)$"), ".json")
            val response = client.newCall(Request.Builder().url(jsonUrl).build()).execute()
            val fetched = response.use { it.body?.string() } ?: return@runCatching null
            metaFile.parentFile?.mkdirs()
            metaFile.writeText(fetched)
            fetched
        }
        val items = JSONObject(body).getJSONArray("items")
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            if (item.optString("link") == chapterUrl) {
                return@runCatching item.optString("summary").takeIf { it.isNotEmpty() }
            }
        }
        null
    }.onFailure { e ->
        Log.w("DeepDive", "Could not load episode JSON context", e)
    }.getOrNull()
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.frybynite.podlore.deepdive.DeepDiveOrchestratorTest"`
Expected: PASS (both tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podlore/deepdive/DeepDiveOrchestrator.kt \
        app/src/test/kotlin/com/frybynite/podlore/deepdive/DeepDiveOrchestratorTest.kt
git commit -m "feat: reuse cached deep dives + cache episode metadata.json"
```

---

### Task 5: PlayerViewModel passes chapter title + stops deleting cached files

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podlore/ui/player/PlayerViewModel.kt`

- [ ] **Step 1: Pass the chapter title into process()**

In `moreAboutThis(...)`, the `process` call currently is:

```kotlin
                val ttsFile = deepDiveOrchestrator.process(resolvedUrl, deepDiveResumeEpisodeUri) { step ->
```

Replace with (resolve the source chapter's title from the index already computed for `_deepDiveChapterIndex`):

```kotlin
                val chapterTitle = _chapters.value.getOrNull(_deepDiveChapterIndex.value ?: -1)?.title
                val ttsFile = deepDiveOrchestrator.process(resolvedUrl, deepDiveResumeEpisodeUri, chapterTitle) { step ->
```

- [ ] **Step 2: Stop deleting the cached TTS file**

The TTS file is now the cache; deleting it would defeat caching. Remove every `pendingTtsFile?.delete()` line (and the immediately following `pendingTtsFile = null` is fine to keep — it only clears the reference). There are occurrences in:
- the `onMediaItemTransition` end block (inside `connect`),
- `skipDeepDive`,
- `jumpToChapter`,
- `onCleared`.

For each, delete the single line:

```kotlin
                                pendingTtsFile?.delete()
```
(and the equivalently-indented lines in the other three methods). Leave the surrounding `pendingTtsFile = null` lines in place.

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run the full unit-test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (no regressions; existing `PlayerViewModelPositionTest`, `PodcastRepositoryTest`, etc. still green).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podlore/ui/player/PlayerViewModel.kt
git commit -m "feat: persist cached deep dives; pass chapter title for cache naming"
```

---

## Final verification

- [ ] Run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` — all green.
- [ ] Manual on-device smoke test:
  - Download an episode → confirm file appears under `filesDir/podcasts/<podcast>/<episode>/audio.*` (via Android Studio Device Explorer).
  - Trigger "More about this" on a chapter → confirm a `more-*.wav` and `metadata.json` appear in the same episode dir.
  - Trigger the same "More about this" again → confirm it plays back immediately with no loading spinner (cache hit).
  - Reopen the podcast → episode list still refreshes (definition updates), cached files remain.
