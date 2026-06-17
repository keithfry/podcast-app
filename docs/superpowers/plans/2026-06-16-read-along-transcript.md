# Read-Along Transcript Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display a sentence-level synchronized transcript in a toggleable bottom sheet that highlights the active sentence and lets users tap any sentence to seek.

**Architecture:** Transcripts are pre-generated JSON files fetched lazily (on toggle) from a URL discovered via the RSS `<podcast:transcript>` tag. No Room storage — transcript JSON is cached to disk keyed by URL hash. Active segment index is derived from `currentPositionMs` already flowing in `PlayerViewModel` and updated inside the existing `updateCurrentChapterIndex()` tick.

**Tech Stack:** Moshi (JSON), OkHttp (fetch via existing `FeedApi`), Compose `ModalBottomSheet`, Hilt, JUnit4 + MockK + MockWebServer (tests).

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/kotlin/com/frybynite/podcastapp/domain/model/TranscriptSegment.kt` | Create | Domain model: `startTimeSec`, `endTimeSec`, `text` |
| `app/src/main/kotlin/com/frybynite/podcastapp/data/network/TranscriptResponse.kt` | Create | Moshi DTOs + `toSegments()` conversion |
| `app/src/main/kotlin/com/frybynite/podcastapp/data/network/FeedApi.kt` | Modify | Add `fetchTranscript(url)` method |
| `app/src/main/kotlin/com/frybynite/podcastapp/domain/model/Episode.kt` | Modify | Add `transcriptUrl: String?` field |
| `app/src/main/kotlin/com/frybynite/podcastapp/data/db/entities/EpisodeEntity.kt` | Modify | Add `transcriptUrl: String?` column |
| `app/src/main/kotlin/com/frybynite/podcastapp/data/di/DatabaseModule.kt` | Modify | Add `MIGRATION_5_6` (ALTER TABLE episodes ADD COLUMN transcriptUrl) |
| `app/src/main/kotlin/com/frybynite/podcastapp/data/db/PodcastDatabase.kt` | Modify | Bump version to 6 |
| `app/src/main/kotlin/com/frybynite/podcastapp/data/repository/PodcastRepository.kt` | Modify | Add `transcriptUrl` to `toDomain()` / `toEntity()` |
| `app/src/main/kotlin/com/frybynite/podcastapp/data/network/RssParser.kt` | Modify | Parse `<podcast:transcript url="...">` tag |
| `app/src/main/kotlin/com/frybynite/podcastapp/data/repository/TranscriptRepository.kt` | Create | Fetch transcript JSON, cache to `filesDir/transcripts/` |
| `app/src/main/kotlin/com/frybynite/podcastapp/data/di/NetworkModule.kt` | Modify | Provide `TranscriptRepository` singleton |
| `app/src/main/kotlin/com/frybynite/podcastapp/ui/player/PlayerViewModel.kt` | Modify | Add transcript StateFlows, `toggleTranscript()`, `seekToSegment()`, active segment update |
| `app/src/main/kotlin/com/frybynite/podcastapp/ui/player/TranscriptPanel.kt` | Create | `ModalBottomSheet` composable: segment list, highlight, auto-scroll, tap-to-seek |
| `app/src/main/kotlin/com/frybynite/podcastapp/ui/player/PlayerScreen.kt` | Modify | Collect transcript state, add toggle button to TopAppBar, show `TranscriptPanel` |
| `docs/specs/database.md` | Modify | Document v6 migration and `transcriptUrl` column |
| `app/src/test/kotlin/com/frybynite/podcastapp/data/network/TranscriptResponseTest.kt` | Create | Moshi parsing + toSegments tests |
| `app/src/test/kotlin/com/frybynite/podcastapp/data/network/RssParserTest.kt` | Modify | Add transcript URL parsing test |
| `app/src/test/kotlin/com/frybynite/podcastapp/data/repository/TranscriptRepositoryTest.kt` | Create | Fetch, cache-hit, cache-miss tests via MockWebServer |

---

## Task 1: TranscriptSegment domain model + TranscriptResponse + FeedApi

**Files:**
- Create: `app/src/main/kotlin/com/frybynite/podcastapp/domain/model/TranscriptSegment.kt`
- Create: `app/src/main/kotlin/com/frybynite/podcastapp/data/network/TranscriptResponse.kt`
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/data/network/FeedApi.kt`
- Create: `app/src/test/kotlin/com/frybynite/podcastapp/data/network/TranscriptResponseTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/kotlin/com/frybynite/podcastapp/data/network/TranscriptResponseTest.kt`:

```kotlin
package com.frybynite.podcastapp.data.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Test
import kotlin.test.assertEquals

class TranscriptResponseTest {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(TranscriptResponse::class.java)

    private val json = """
        {
          "version": "1.0.0",
          "segments": [
            {"startTime": 0.0, "endTime": 3.2, "text": "Welcome to AI Daily Radar."},
            {"startTime": 3.2, "endTime": 7.8, "text": "Today we have twelve stories."}
          ]
        }
    """.trimIndent()

    @Test fun `parses two segments`() {
        val response = adapter.fromJson(json)!!
        assertEquals(2, response.segments.size)
    }

    @Test fun `parses segment fields`() {
        val seg = adapter.fromJson(json)!!.segments[1]
        assertEquals(3.2f, seg.startTime)
        assertEquals(7.8f, seg.endTime)
        assertEquals("Today we have twelve stories.", seg.text)
    }

    @Test fun `toSegments maps to domain model`() {
        val segments = adapter.fromJson(json)!!.toSegments()
        assertEquals(2, segments.size)
        assertEquals(0.0f, segments[0].startTimeSec)
        assertEquals(3.2f, segments[0].endTimeSec)
        assertEquals("Welcome to AI Daily Radar.", segments[0].text)
    }

    @Test fun `toSegments preserves order`() {
        val segments = adapter.fromJson(json)!!.toSegments()
        assertEquals(3.2f, segments[1].startTimeSec)
    }

    @Test fun `empty segments list`() {
        val response = adapter.fromJson("""{"version":"1.0.0","segments":[]}""")!!
        assertEquals(emptyList(), response.toSegments())
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```
./gradlew :app:testDebugUnitTest --tests "com.frybynite.podcastapp.data.network.TranscriptResponseTest" 2>&1 | tail -20
```

Expected: compilation error — `TranscriptResponse` not found.

- [ ] **Step 3: Create `TranscriptSegment` domain model**

Create `app/src/main/kotlin/com/frybynite/podcastapp/domain/model/TranscriptSegment.kt`:

```kotlin
package com.frybynite.podcastapp.domain.model

data class TranscriptSegment(
    val startTimeSec: Float,
    val endTimeSec: Float,
    val text: String
)
```

- [ ] **Step 4: Create `TranscriptResponse`**

Create `app/src/main/kotlin/com/frybynite/podcastapp/data/network/TranscriptResponse.kt`:

```kotlin
package com.frybynite.podcastapp.data.network

import com.frybynite.podcastapp.domain.model.TranscriptSegment
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TranscriptResponse(
    @Json(name = "version") val version: String = "",
    @Json(name = "segments") val segments: List<TranscriptSegmentDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TranscriptSegmentDto(
    @Json(name = "startTime") val startTime: Float = 0f,
    @Json(name = "endTime") val endTime: Float = 0f,
    @Json(name = "text") val text: String = ""
)

fun TranscriptResponse.toSegments(): List<TranscriptSegment> =
    segments.map { TranscriptSegment(it.startTime, it.endTime, it.text) }
```

- [ ] **Step 5: Add `fetchTranscript` to `FeedApi`**

In `app/src/main/kotlin/com/frybynite/podcastapp/data/network/FeedApi.kt`, add after the `fetchChapters` method:

```kotlin
    suspend fun fetchTranscript(url: String): TranscriptResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val json = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            response.body?.string() ?: throw Exception("Empty body")
        }
        moshi.adapter(TranscriptResponse::class.java).fromJson(json)
            ?: throw Exception("Failed to parse transcript JSON")
    }
```

- [ ] **Step 6: Run tests to confirm they pass**

```
./gradlew :app:testDebugUnitTest --tests "com.frybynite.podcastapp.data.network.TranscriptResponseTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, all 5 tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/domain/model/TranscriptSegment.kt
git add app/src/main/kotlin/com/frybynite/podcastapp/data/network/TranscriptResponse.kt
git add app/src/main/kotlin/com/frybynite/podcastapp/data/network/FeedApi.kt
git add app/src/test/kotlin/com/frybynite/podcastapp/data/network/TranscriptResponseTest.kt
git commit -m "feat: add TranscriptSegment domain model, TranscriptResponse DTOs, FeedApi.fetchTranscript"
```

---

## Task 2: Add `transcriptUrl` to Episode + DB migration

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/domain/model/Episode.kt`
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/data/db/entities/EpisodeEntity.kt`
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/data/di/DatabaseModule.kt`
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/data/db/PodcastDatabase.kt`
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/data/repository/PodcastRepository.kt`
- Modify: `docs/specs/database.md`

- [ ] **Step 1: Add `transcriptUrl` to `Episode`**

In `app/src/main/kotlin/com/frybynite/podcastapp/domain/model/Episode.kt`, add the field after `chaptersUrl`:

```kotlin
data class Episode(
    val audioUrl: String,
    val podcastFeedUrl: String,
    val title: String,
    val pubDate: Long,
    val durationSeconds: Int,
    val chaptersUrl: String?,
    val transcriptUrl: String? = null,
    val imageUrl: String? = null,
    val downloadPath: String? = null,
    val downloadStatus: DownloadStatus = DownloadStatus.NONE,
    val lastPositionMs: Long = 0L,
    val isHeard: Boolean = false
)
```

- [ ] **Step 2: Add `transcriptUrl` to `EpisodeEntity`**

In `app/src/main/kotlin/com/frybynite/podcastapp/data/db/entities/EpisodeEntity.kt`, add the field after `chaptersUrl`:

```kotlin
@Entity(tableName = "episodes", indices = [Index(value = ["podcastFeedUrl"])])
data class EpisodeEntity(
    @PrimaryKey val audioUrl: String,
    val podcastFeedUrl: String,
    val title: String,
    val pubDate: Long,
    val durationSeconds: Int,
    val chaptersUrl: String?,
    val transcriptUrl: String? = null,
    val imageUrl: String? = null,
    val downloadPath: String?,
    val downloadStatus: String = "NONE",
    val lastPositionMs: Long = 0L,
    val isHeard: Boolean = false
)
```

- [ ] **Step 3: Add `MIGRATION_5_6` and update `DatabaseModule`**

In `app/src/main/kotlin/com/frybynite/podcastapp/data/di/DatabaseModule.kt`, add after `MIGRATION_4_5`:

```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE episodes ADD COLUMN transcriptUrl TEXT")
    }
}
```

Then update the `provideDatabase` method's `addMigrations` call:

```kotlin
        Room.databaseBuilder(ctx, PodcastDatabase::class.java, "podcast.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()
```

- [ ] **Step 4: Bump `PodcastDatabase` version to 6**

In `app/src/main/kotlin/com/frybynite/podcastapp/data/db/PodcastDatabase.kt`, change:

```kotlin
@Database(
    entities = [PodcastEntity::class, EpisodeEntity::class, ChapterEntity::class, DeepDiveEntity::class],
    version = 6,
    exportSchema = false
)
```

- [ ] **Step 5: Update `toDomain()` and `toEntity()` in `PodcastRepository`**

In `app/src/main/kotlin/com/frybynite/podcastapp/data/repository/PodcastRepository.kt`, update the mapping functions:

```kotlin
fun EpisodeEntity.toDomain() = Episode(
    audioUrl = audioUrl, podcastFeedUrl = podcastFeedUrl, title = title,
    pubDate = pubDate, durationSeconds = durationSeconds, chaptersUrl = chaptersUrl,
    transcriptUrl = transcriptUrl,
    imageUrl = imageUrl, downloadPath = downloadPath,
    downloadStatus = DownloadStatus.valueOf(downloadStatus), lastPositionMs = lastPositionMs,
    isHeard = isHeard
)
fun Episode.toEntity(feedUrl: String) = EpisodeEntity(
    audioUrl = audioUrl, podcastFeedUrl = feedUrl, title = title, pubDate = pubDate,
    durationSeconds = durationSeconds, chaptersUrl = chaptersUrl,
    transcriptUrl = transcriptUrl,
    imageUrl = imageUrl,
    downloadPath = downloadPath, downloadStatus = downloadStatus.name
)
```

- [ ] **Step 6: Update `docs/specs/database.md`**

In the `episodes` table, add a row after `chaptersUrl`:

```
| `transcriptUrl` | TEXT | NULL | — | URL for pre-generated transcript JSON (`podcast:transcript` tag) |
```

At the bottom of the version history table, add:

```
| 6 | Added `transcriptUrl` to `episodes`; URL for pre-generated sentence-level transcript JSON |
```

Also update the header: `Current version: **6**`

- [ ] **Step 7: Build to confirm no compile errors**

```
./gradlew :app:assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/domain/model/Episode.kt
git add app/src/main/kotlin/com/frybynite/podcastapp/data/db/entities/EpisodeEntity.kt
git add app/src/main/kotlin/com/frybynite/podcastapp/data/di/DatabaseModule.kt
git add app/src/main/kotlin/com/frybynite/podcastapp/data/db/PodcastDatabase.kt
git add app/src/main/kotlin/com/frybynite/podcastapp/data/repository/PodcastRepository.kt
git add docs/specs/database.md
git commit -m "feat: add transcriptUrl to Episode/EpisodeEntity, DB migration v5->v6"
```

---

## Task 3: Parse `<podcast:transcript>` in RSS feed

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/data/network/RssParser.kt`
- Modify: `app/src/test/kotlin/com/frybynite/podcastapp/data/network/RssParserTest.kt`

- [ ] **Step 1: Write the failing test**

Open `app/src/test/kotlin/com/frybynite/podcastapp/data/network/RssParserTest.kt`. Add this test at the end of the existing test class:

```kotlin
    @Test fun `parses podcast transcript tag url`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0"
     xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"
     xmlns:podcast="https://podcastindex.org/namespace/1.0">
  <channel>
    <title>Test Podcast</title>
    <link>https://example.com</link>
    <item>
      <title>Episode 1</title>
      <enclosure url="https://example.com/ep1.mp3" type="audio/mpeg" length="12345"/>
      <pubDate>Mon, 01 Jan 2024 00:00:00 +0000</pubDate>
      <itunes:duration>300</itunes:duration>
      <podcast:transcript url="https://example.com/ep1-2024-01-01.transcript.json" type="application/json"/>
    </item>
  </channel>
</rss>"""
        val feed = RssParser().parse(xml)
        assertEquals("https://example.com/ep1-2024-01-01.transcript.json", feed.episodes[0].transcriptUrl)
    }

    @Test fun `transcriptUrl is null when tag absent`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0"
     xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"
     xmlns:podcast="https://podcastindex.org/namespace/1.0">
  <channel>
    <title>Test Podcast</title>
    <link>https://example.com</link>
    <item>
      <title>Episode 1</title>
      <enclosure url="https://example.com/ep1.mp3" type="audio/mpeg" length="12345"/>
      <pubDate>Mon, 01 Jan 2024 00:00:00 +0000</pubDate>
      <itunes:duration>300</itunes:duration>
    </item>
  </channel>
</rss>"""
        val feed = RssParser().parse(xml)
        assertNull(feed.episodes[0].transcriptUrl)
    }
```

Make sure `assertNull` is imported: `import kotlin.test.assertNull`

- [ ] **Step 2: Run test to confirm it fails**

```
./gradlew :app:testDebugUnitTest --tests "com.frybynite.podcastapp.data.network.RssParserTest" 2>&1 | tail -20
```

Expected: FAIL — `transcriptUrl` returns `null` when tag is present.

- [ ] **Step 3: Update `RssParser` to parse the transcript tag**

In `app/src/main/kotlin/com/frybynite/podcastapp/data/network/RssParser.kt`:

In the `parse()` function, add `var epTranscriptUrl: String? = null` alongside the other episode variables (line ~42). Add it to the reset block inside `parser.name == "item"` START_TAG handling (line ~55):

```kotlin
                        parser.name == "item" -> {
                            inItem = true
                            epTitle = ""; epAudioUrl = ""; epPubDate = 0L
                            epDurationSeconds = 0; epChaptersUrl = null; epImageUrl = null
                            epTranscriptUrl = null
                        }
```

Add the transcript tag parsing in the START_TAG `when` block, directly after the `podcast:chapters` line (line ~59):

```kotlin
                        parser.namespace == NS_PODCAST && parser.name == "transcript" ->
                            if (inItem) epTranscriptUrl = parser.getAttributeValue(null, "url")
```

In the END_TAG `parser.name == "item"` block, add `transcriptUrl = epTranscriptUrl` to the `Episode(...)` constructor call:

```kotlin
                    parser.name == "item" -> {
                        episodes.add(
                            Episode(
                                audioUrl = epAudioUrl,
                                podcastFeedUrl = podcastLink,
                                title = epTitle,
                                pubDate = epPubDate,
                                durationSeconds = epDurationSeconds,
                                chaptersUrl = epChaptersUrl,
                                transcriptUrl = epTranscriptUrl,
                                imageUrl = epImageUrl
                            )
                        )
                        inItem = false
                    }
```

- [ ] **Step 4: Run tests to confirm they pass**

```
./gradlew :app:testDebugUnitTest --tests "com.frybynite.podcastapp.data.network.RssParserTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/data/network/RssParser.kt
git add app/src/test/kotlin/com/frybynite/podcastapp/data/network/RssParserTest.kt
git commit -m "feat: parse podcast:transcript tag from RSS feed into Episode.transcriptUrl"
```

---

## Task 4: TranscriptRepository

**Files:**
- Create: `app/src/main/kotlin/com/frybynite/podcastapp/data/repository/TranscriptRepository.kt`
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/data/di/NetworkModule.kt`
- Create: `app/src/test/kotlin/com/frybynite/podcastapp/data/repository/TranscriptRepositoryTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/kotlin/com/frybynite/podcastapp/data/repository/TranscriptRepositoryTest.kt`:

```kotlin
package com.frybynite.podcastapp.data.repository

import com.frybynite.podcastapp.data.network.FeedApi
import com.frybynite.podcastapp.data.network.TranscriptResponse
import com.frybynite.podcastapp.data.network.TranscriptSegmentDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class TranscriptRepositoryTest {

    private val server = MockWebServer()
    private lateinit var repo: TranscriptRepository
    private lateinit var tempDir: File

    @Before fun setUp() {
        server.start()
        tempDir = createTempDir()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        repo = TranscriptRepository(FeedApi(OkHttpClient(), moshi), moshi, tempDir)
    }

    @After fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    companion object {
        val TRANSCRIPT_JSON = """
            {
              "version": "1.0.0",
              "segments": [
                {"startTime": 0.0, "endTime": 3.2, "text": "Welcome."},
                {"startTime": 3.2, "endTime": 7.8, "text": "Today we begin."}
              ]
            }
        """.trimIndent()
    }

    @Test fun `fetchTranscript returns parsed segments`() = runTest {
        server.enqueue(MockResponse().setBody(TRANSCRIPT_JSON).setResponseCode(200))

        val segments = repo.fetchTranscript(server.url("/t.json").toString())

        assertEquals(2, segments.size)
        assertEquals("Welcome.", segments[0].text)
        assertEquals(0.0f, segments[0].startTimeSec)
        assertEquals(3.2f, segments[0].endTimeSec)
    }

    @Test fun `fetchTranscript caches to disk on first fetch`() = runTest {
        server.enqueue(MockResponse().setBody(TRANSCRIPT_JSON).setResponseCode(200))
        val url = server.url("/t.json").toString()

        repo.fetchTranscript(url)

        val cacheFile = File(tempDir, "${Math.abs(url.hashCode())}.json")
        assertEquals(true, cacheFile.exists())
    }

    @Test fun `fetchTranscript uses disk cache on second call`() = runTest {
        server.enqueue(MockResponse().setBody(TRANSCRIPT_JSON).setResponseCode(200))
        val url = server.url("/t.json").toString()

        repo.fetchTranscript(url)
        repo.fetchTranscript(url)

        assertEquals(1, server.requestCount)
    }

    @Test fun `fetchTranscript cache hit returns correct segments`() = runTest {
        server.enqueue(MockResponse().setBody(TRANSCRIPT_JSON).setResponseCode(200))
        val url = server.url("/t.json").toString()

        repo.fetchTranscript(url)
        val segments = repo.fetchTranscript(url)

        assertEquals(2, segments.size)
        assertEquals("Today we begin.", segments[1].text)
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```
./gradlew :app:testDebugUnitTest --tests "com.frybynite.podcastapp.data.repository.TranscriptRepositoryTest" 2>&1 | tail -20
```

Expected: compilation error — `TranscriptRepository` not found.

- [ ] **Step 3: Create `TranscriptRepository`**

Create `app/src/main/kotlin/com/frybynite/podcastapp/data/repository/TranscriptRepository.kt`:

```kotlin
package com.frybynite.podcastapp.data.repository

import android.util.Log
import com.frybynite.podcastapp.data.network.FeedApi
import com.frybynite.podcastapp.data.network.TranscriptResponse
import com.frybynite.podcastapp.data.network.toSegments
import com.frybynite.podcastapp.domain.model.TranscriptSegment
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "TranscriptRepo"

class TranscriptRepository(
    private val feedApi: FeedApi,
    private val moshi: Moshi,
    private val transcriptsDir: File
) {
    init { transcriptsDir.mkdirs() }

    private val adapter by lazy { moshi.adapter(TranscriptResponse::class.java) }

    suspend fun fetchTranscript(transcriptUrl: String): List<TranscriptSegment> =
        withContext(Dispatchers.IO) {
            val cacheFile = File(transcriptsDir, "${Math.abs(transcriptUrl.hashCode())}.json")
            if (cacheFile.exists()) {
                Log.d(TAG, "fetchTranscript: cache hit for $transcriptUrl")
                return@withContext adapter.fromJson(cacheFile.readText())?.toSegments() ?: emptyList()
            }
            Log.i(TAG, "fetchTranscript: fetching $transcriptUrl")
            val response = feedApi.fetchTranscript(transcriptUrl)
            cacheFile.writeText(adapter.toJson(response))
            response.toSegments()
        }
}
```

- [ ] **Step 4: Wire `TranscriptRepository` in `NetworkModule`**

In `app/src/main/kotlin/com/frybynite/podcastapp/data/di/NetworkModule.kt`, add these imports at the top:

```kotlin
import android.content.Context
import com.frybynite.podcastapp.data.repository.TranscriptRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
```

Then add at the end of the `NetworkModule` object:

```kotlin
    @Provides
    @Singleton
    fun provideTranscriptRepository(
        feedApi: FeedApi,
        moshi: Moshi,
        @ApplicationContext context: Context
    ): TranscriptRepository = TranscriptRepository(feedApi, moshi, File(context.filesDir, "transcripts"))
```

- [ ] **Step 5: Run tests to confirm they pass**

```
./gradlew :app:testDebugUnitTest --tests "com.frybynite.podcastapp.data.repository.TranscriptRepositoryTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, all 4 tests pass.

- [ ] **Step 6: Build to confirm Hilt wiring compiles**

```
./gradlew :app:assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/data/repository/TranscriptRepository.kt
git add app/src/main/kotlin/com/frybynite/podcastapp/data/di/NetworkModule.kt
git add app/src/test/kotlin/com/frybynite/podcastapp/data/repository/TranscriptRepositoryTest.kt
git commit -m "feat: add TranscriptRepository with disk-based JSON caching"
```

---

## Task 5: PlayerViewModel transcript state

**Files:**
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/ui/player/PlayerViewModel.kt`

- [ ] **Step 1: Add transcript StateFlows and jobs**

In `PlayerViewModel.kt`, after the `cachedDeepDiveJob` declaration (around line 110), add:

```kotlin
    private val _transcriptSegments = MutableStateFlow<List<TranscriptSegment>>(emptyList())
    val transcriptSegments: StateFlow<List<TranscriptSegment>> = _transcriptSegments.asStateFlow()

    private val _activeSegmentIndex = MutableStateFlow(-1)
    val activeSegmentIndex: StateFlow<Int> = _activeSegmentIndex.asStateFlow()

    private val _showTranscript = MutableStateFlow(false)
    val showTranscript: StateFlow<Boolean> = _showTranscript.asStateFlow()

    private val _transcriptLoading = MutableStateFlow(false)
    val transcriptLoading: StateFlow<Boolean> = _transcriptLoading.asStateFlow()

    private val _hasTranscript = MutableStateFlow(false)
    val hasTranscript: StateFlow<Boolean> = _hasTranscript.asStateFlow()

    private var transcriptJob: Job? = null
```

Add the import at the top of the file:

```kotlin
import com.frybynite.podcastapp.data.repository.TranscriptRepository
import com.frybynite.podcastapp.domain.model.TranscriptSegment
```

- [ ] **Step 2: Inject `TranscriptRepository` into the ViewModel constructor**

Add `private val transcriptRepo: TranscriptRepository` to the `@HiltViewModel` constructor parameter list in `PlayerViewModel.kt`:

```kotlin
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chapterRepo: ChapterRepository,
    private val episodeDao: EpisodeDao,
    private val podcastDao: PodcastDao,
    private val podcastRepo: com.frybynite.podcastapp.data.repository.PodcastRepository,
    private val deepDiveDao: com.frybynite.podcastapp.data.db.dao.DeepDiveDao,
    private val speedPrefs: SpeedPreferences,
    private val deepDiveOrchestrator: DeepDiveOrchestrator,
    private val summarizer: TextSummarizer,
    private val modelDownloadManager: ModelDownloadManager,
    private val transcriptRepo: TranscriptRepository
) : ViewModel() {
```

- [ ] **Step 3: Reset transcript state in `loadMetadata`**

In `loadMetadata()`, in the synchronous reset block (around line 272–282, before the `viewModelScope.launch` block), add:

```kotlin
        transcriptJob?.cancel()
        transcriptJob = null
        _transcriptSegments.value = emptyList()
        _activeSegmentIndex.value = -1
        _showTranscript.value = false
        _hasTranscript.value = false
```

In the `viewModelScope.launch` block inside `loadMetadata`, after `currentEpisode = episode` (around line 306), add:

```kotlin
            _hasTranscript.value = episode.transcriptUrl != null
```

- [ ] **Step 4: Add `toggleTranscript` and `loadTranscript` functions**

Add these functions after `setSleepTimer` or near the end of the class:

```kotlin
    fun toggleTranscript() {
        val url = currentEpisode?.transcriptUrl ?: return
        val next = !_showTranscript.value
        _showTranscript.value = next
        if (next && _transcriptSegments.value.isEmpty()) {
            loadTranscript(url)
        }
    }

    private fun loadTranscript(url: String) {
        transcriptJob?.cancel()
        transcriptJob = viewModelScope.launch {
            _transcriptLoading.value = true
            runCatching { transcriptRepo.fetchTranscript(url) }
                .onSuccess { _transcriptSegments.value = it }
                .onFailure { Log.e(TAG, "loadTranscript failed", it) }
            _transcriptLoading.value = false
        }
    }

    fun seekToSegment(segment: TranscriptSegment) {
        controller?.seekTo((segment.startTimeSec * 1000).toLong())
    }
```

- [ ] **Step 5: Update active segment index in `updateCurrentChapterIndex`**

In `updateCurrentChapterIndex()` (line ~351), after the chapter index update block (after line ~376), add:

```kotlin
        val segments = _transcriptSegments.value
        if (segments.isNotEmpty()) {
            val posSec = pos / 1000f
            val idx = segments.indexOfLast { it.startTimeSec <= posSec }
            _activeSegmentIndex.value = if (idx >= 0 && posSec < segments[idx].endTimeSec) idx else -1
        }
```

- [ ] **Step 6: Build to confirm no compile errors**

```
./gradlew :app:assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/ui/player/PlayerViewModel.kt
git commit -m "feat: add transcript state, toggleTranscript, seekToSegment, active segment tracking to PlayerViewModel"
```

---

## Task 6: TranscriptPanel composable + PlayerScreen integration

**Files:**
- Create: `app/src/main/kotlin/com/frybynite/podcastapp/ui/player/TranscriptPanel.kt`
- Modify: `app/src/main/kotlin/com/frybynite/podcastapp/ui/player/PlayerScreen.kt`

- [ ] **Step 1: Create `TranscriptPanel`**

Create `app/src/main/kotlin/com/frybynite/podcastapp/ui/player/TranscriptPanel.kt`:

```kotlin
package com.frybynite.podcastapp.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.frybynite.podcastapp.domain.model.TranscriptSegment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptPanel(
    segments: List<TranscriptSegment>,
    activeIndex: Int,
    loading: Boolean,
    onSeek: (TranscriptSegment) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val listState = rememberLazyListState()
            LaunchedEffect(activeIndex) {
                if (activeIndex >= 0) listState.animateScrollToItem(activeIndex)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                itemsIndexed(segments) { idx, segment ->
                    val isActive = idx == activeIndex
                    Text(
                        text = segment.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSeek(segment) }
                            .background(
                                color = if (isActive) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent,
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                    )
                    if (idx < segments.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Collect transcript state in `PlayerScreen`**

In `app/src/main/kotlin/com/frybynite/podcastapp/ui/player/PlayerScreen.kt`, after the existing `collectAsStateWithLifecycle` calls (around line 83), add:

```kotlin
    val hasTranscript by vm.hasTranscript.collectAsStateWithLifecycle()
    val showTranscript by vm.showTranscript.collectAsStateWithLifecycle()
    val transcriptSegments by vm.transcriptSegments.collectAsStateWithLifecycle()
    val activeSegmentIndex by vm.activeSegmentIndex.collectAsStateWithLifecycle()
    val transcriptLoading by vm.transcriptLoading.collectAsStateWithLifecycle()
```

Add the import at the top of `PlayerScreen.kt`:

```kotlin
import androidx.compose.material.icons.filled.Article
```

- [ ] **Step 3: Add transcript toggle button to `TopAppBar` actions**

In `PlayerScreen.kt`, in the `TopAppBar` `actions` block (around line 195), add the transcript button before the existing sleep timer button:

```kotlin
                    if (hasTranscript) {
                        IconButton(onClick = { vm.toggleTranscript() }) {
                            Icon(Icons.Filled.Article, "Transcript")
                        }
                    }
```

- [ ] **Step 4: Show `TranscriptPanel` when toggled**

In `PlayerScreen.kt`, in the bottom sheet section (around line 644, after the existing `SleepTimerBottomSheet` and `SpeedBottomSheet` `if` blocks), add:

```kotlin
    if (showTranscript) {
        TranscriptPanel(
            segments = transcriptSegments,
            activeIndex = activeSegmentIndex,
            loading = transcriptLoading,
            onSeek = { segment -> vm.seekToSegment(segment) },
            onDismiss = { vm.toggleTranscript() }
        )
    }
```

- [ ] **Step 5: Build to confirm no compile errors**

```
./gradlew :app:assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Run all tests**

```
./gradlew :app:testDebugUnitTest 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`, no test failures.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/frybynite/podcastapp/ui/player/TranscriptPanel.kt
git add app/src/main/kotlin/com/frybynite/podcastapp/ui/player/PlayerScreen.kt
git commit -m "feat: add TranscriptPanel bottom sheet and toggle button to PlayerScreen"
```

---

## Self-Review

### Spec coverage

| Requirement | Task |
|-------------|------|
| Discover transcript URL from RSS `<podcast:transcript>` | Task 3 (RssParser) |
| Store URL on episode | Task 2 (Episode, EpisodeEntity, migration) |
| Fetch JSON lazily on toggle | Task 4 (TranscriptRepository), Task 5 (toggleTranscript) |
| Cache JSON if offline | Task 4 (disk cache) |
| Toggle button, hidden by default | Task 6 (`hasTranscript` guard, `showTranscript` starts false) |
| Render all segments as scrollable list | Task 6 (TranscriptPanel LazyColumn) |
| Highlight segment where startTime ≤ currentTime < endTime | Task 5 (`updateCurrentChapterIndex` update) |
| Auto-scroll highlighted segment into view | Task 6 (`LaunchedEffect(activeIndex)`) |
| Tap segment → seek to startTime | Task 5 (`seekToSegment`), Task 6 (`onSeek`) |
| Works offline if cached | Task 4 (cache-hit path reads from disk) |
| Highlight survives scrubbing and chapter jumps | Task 5 (index recomputed on every `updateCurrentChapterIndex` call, which fires on `onPositionDiscontinuity`) |
| DB schema documentation | Task 2 (`docs/specs/database.md`) |

All requirements covered.

### Placeholder scan

No TBD/TODO placeholders. All steps include actual code.

### Type consistency

- `TranscriptSegment.startTimeSec: Float` / `endTimeSec: Float` — used consistently in `updateCurrentChapterIndex` (`pos / 1000f`, float comparison) and `seekToSegment` (`* 1000).toLong()`).
- `TranscriptSegmentDto.startTime: Float` maps to `TranscriptSegment.startTimeSec` — verified in `toSegments()`.
- `_activeSegmentIndex` initialized to `-1`, `TranscriptPanel` guards `if (activeIndex >= 0)` before scroll — consistent.
- `toggleTranscript` toggles `_showTranscript` and dismissal calls `vm.toggleTranscript()` again — correct bidirectional toggle.
