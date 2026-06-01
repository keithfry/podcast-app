# Podcast App Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a native Android podcast player with chapter-aware playback, per-chapter link actions, voice control, and an Android Auto foundation.

**Architecture:** MVVM with Repository pattern. `PlaybackService` extends `MediaLibraryService` (Media3) and runs as a foreground service holding the single `ExoPlayer` instance. UI connects via `MediaController`. Room caches subscriptions, episodes, and chapters locally.

**Tech Stack:** Kotlin · Jetpack Compose · Media3/ExoPlayer · Room · Hilt · WorkManager · OkHttp · Moshi · XmlPullParser (RSS) · Min API 29

---

## Task 0: Git init + project scaffold

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (root)
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/com/podcastapp/MainActivity.kt`
- Create: `.gitignore`

**Step 1: Init git**

```bash
cd /Users/keithfry/projects/podcast-app
git init
```

**Step 2: Create `.gitignore`**

```
.gradle/
.idea/
build/
*.iml
local.properties
*.jks
```

**Step 3: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "PodcastApp"
include(":app")
```

**Step 4: Create root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
```

**Step 5: Create `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.5.0"
kotlin = "2.0.0"
ksp = "2.0.0-1.0.21"
hilt = "2.51.1"
compose-bom = "2024.06.00"
media3 = "1.3.1"
room = "2.6.1"
retrofit = "2.11.0"
okhttp = "4.12.0"
moshi = "1.15.1"
coil = "2.7.0"
work = "2.9.0"
navigation = "2.7.7"
coroutines = "1.8.1"
lifecycle = "2.8.3"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version = "1.13.1" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version = "1.9.0" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "media3" }
media3-datasource-okhttp = { group = "androidx.media3", name = "media3-datasource-okhttp", version.ref = "media3" }
media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version = "1.2.0" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
moshi-kotlin = { group = "com.squareup.moshi", name = "moshi-kotlin", version.ref = "moshi" }
moshi-codegen = { group = "com.squareup.moshi", name = "moshi-kotlin-codegen", version.ref = "moshi" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
hilt-work = { group = "androidx.hilt", name = "hilt-work", version = "1.2.0" }
hilt-work-compiler = { group = "androidx.hilt", name = "hilt-compiler", version = "1.2.0" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { group = "junit", name = "junit", version = "4.13.2" }
mockk = { group = "io.mockk", name = "mockk", version = "1.13.11" }
turbine = { group = "app.cash.turbine", name = "turbine", version = "1.1.0" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version = "1.2.1" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

**Step 6: Create `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.podcastapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.podcastapp"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.navigation.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.okhttp)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.codegen)
    implementation(libs.coil.compose)
    implementation(libs.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
```

**Step 7: Create `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".PodcastApplication"
        android:allowBackup="true"
        android:label="Podcast App"
        android:supportsRtl="true"
        android:theme="@style/Theme.AppCompat.DayNight.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.PlaybackService"
            android:exported="true"
            android:foregroundServiceType="mediaPlayback">
            <intent-filter>
                <action android:name="androidx.media3.session.MediaLibraryService" />
                <action android:name="android.media.browse.MediaBrowserService" />
            </intent-filter>
        </service>

    </application>
</manifest>
```

**Step 8: Create Application class and MainActivity stub**

`app/src/main/kotlin/com/podcastapp/PodcastApplication.kt`:
```kotlin
package com.podcastapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PodcastApplication : Application()
```

`app/src/main/kotlin/com/podcastapp/MainActivity.kt`:
```kotlin
package com.podcastapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.podcastapp.ui.PodcastNavGraph
import com.podcastapp.ui.theme.PodcastAppTheme
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
    }
}
```

**Step 9: Create minimal theme + nav stub so project compiles**

`app/src/main/kotlin/com/podcastapp/ui/theme/Theme.kt`:
```kotlin
package com.podcastapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun PodcastAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
```

`app/src/main/kotlin/com/podcastapp/ui/PodcastNavGraph.kt`:
```kotlin
package com.podcastapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.material3.Text

@Composable
fun PodcastNavGraph() {
    Text("Hello Podcast App")
}
```

**Step 10: Verify project builds**

```bash
cd /Users/keithfry/projects/podcast-app
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

**Step 11: Commit**

```bash
git add .
git commit -m "feat: scaffold Android project with Compose, Media3, Room, Hilt"
```

---

## Task 1: Domain models

**Files:**
- Create: `app/src/main/kotlin/com/podcastapp/domain/model/Podcast.kt`
- Create: `app/src/main/kotlin/com/podcastapp/domain/model/Episode.kt`
- Create: `app/src/main/kotlin/com/podcastapp/domain/model/Chapter.kt`
- Create: `app/src/test/kotlin/com/podcastapp/domain/model/ModelTest.kt`

**Step 1: Write failing test**

`app/src/test/kotlin/com/podcastapp/domain/model/ModelTest.kt`:
```kotlin
package com.podcastapp.domain.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelTest {
    @Test fun `chapter contains url`() {
        val c = Chapter(episodeAudioUrl = "http://ep.mp3", startTimeMs = 0, endTimeMs = 5000,
            title = "Intro", url = "https://example.com")
        assertEquals("https://example.com", c.url)
    }

    @Test fun `chapter url is nullable`() {
        val c = Chapter(episodeAudioUrl = "http://ep.mp3", startTimeMs = 0, endTimeMs = 5000,
            title = "Intro", url = null)
        assertNull(c.url)
    }

    @Test fun `episode download status defaults to NONE`() {
        val e = Episode(audioUrl = "http://ep.mp3", podcastFeedUrl = "http://feed.xml",
            title = "Ep 1", pubDate = 0L, durationSeconds = 60, chaptersUrl = null)
        assertEquals(DownloadStatus.NONE, e.downloadStatus)
    }
}
```

**Step 2: Run test — expect FAIL (classes not defined)**

```bash
./gradlew :app:test --tests "com.podcastapp.domain.model.ModelTest" 2>&1 | tail -20
```

**Step 3: Create models**

`app/src/main/kotlin/com/podcastapp/domain/model/Podcast.kt`:
```kotlin
package com.podcastapp.domain.model

data class Podcast(
    val feedUrl: String,
    val title: String,
    val author: String,
    val description: String,
    val imageUrl: String?,
    val lastUpdated: Long = 0L
)
```

`app/src/main/kotlin/com/podcastapp/domain/model/Episode.kt`:
```kotlin
package com.podcastapp.domain.model

data class Episode(
    val audioUrl: String,
    val podcastFeedUrl: String,
    val title: String,
    val pubDate: Long,
    val durationSeconds: Int,
    val chaptersUrl: String?,
    val downloadPath: String? = null,
    val downloadStatus: DownloadStatus = DownloadStatus.NONE
)

enum class DownloadStatus { NONE, QUEUED, DOWNLOADING, DONE }
```

`app/src/main/kotlin/com/podcastapp/domain/model/Chapter.kt`:
```kotlin
package com.podcastapp.domain.model

data class Chapter(
    val id: Long = 0,
    val episodeAudioUrl: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val title: String,
    val url: String?
)
```

**Step 4: Run test — expect PASS**

```bash
./gradlew :app:test --tests "com.podcastapp.domain.model.ModelTest"
```

**Step 5: Commit**

```bash
git add app/src/main/kotlin/com/podcastapp/domain/ app/src/test/kotlin/com/podcastapp/domain/
git commit -m "feat: add domain models Podcast, Episode, Chapter"
```

---

## Task 2: RSS parser

**Files:**
- Create: `app/src/main/kotlin/com/podcastapp/data/network/RssParser.kt`
- Create: `app/src/test/kotlin/com/podcastapp/data/network/RssParserTest.kt`
- Create: `app/src/test/resources/test_feed.xml`

**Step 1: Create test fixture `app/src/test/resources/test_feed.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0"
  xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"
  xmlns:podcast="https://podcastindex.org/namespace/1.0">
  <channel>
    <title>Test Podcast</title>
    <link>https://example.com</link>
    <description>A test podcast</description>
    <itunes:author>Test Author</itunes:author>
    <item>
      <title>Episode 1</title>
      <pubDate>Mon, 01 Jun 2026 08:00:00 +0000</pubDate>
      <enclosure url="https://example.com/ep1.mp3" type="audio/mpeg" length="1000"/>
      <itunes:duration>00:10:00</itunes:duration>
      <guid isPermaLink="true">https://example.com/ep1.mp3</guid>
      <description>First episode</description>
      <podcast:chapters url="https://example.com/ep1.chapters.json" type="application/json+chapters"/>
    </item>
    <item>
      <title>Episode 2</title>
      <pubDate>Tue, 02 Jun 2026 08:00:00 +0000</pubDate>
      <enclosure url="https://example.com/ep2.mp3" type="audio/mpeg" length="2000"/>
      <itunes:duration>00:20:30</itunes:duration>
      <guid isPermaLink="true">https://example.com/ep2.mp3</guid>
      <description>Second episode</description>
    </item>
  </channel>
</rss>
```

**Step 2: Write failing test**

`app/src/test/kotlin/com/podcastapp/data/network/RssParserTest.kt`:
```kotlin
package com.podcastapp.data.network

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class RssParserTest {
    private val parser = RssParser()
    private val xml = RssParserTest::class.java.classLoader!!
        .getResourceAsStream("test_feed.xml")!!.bufferedReader().readText()

    @Test fun `parses podcast title and author`() {
        val result = parser.parse(xml)
        assertEquals("Test Podcast", result.podcast.title)
        assertEquals("Test Author", result.podcast.author)
    }

    @Test fun `parses two episodes`() {
        val result = parser.parse(xml)
        assertEquals(2, result.episodes.size)
    }

    @Test fun `parses episode fields`() {
        val result = parser.parse(xml)
        val ep = result.episodes.first()
        assertEquals("Episode 1", ep.title)
        assertEquals("https://example.com/ep1.mp3", ep.audioUrl)
        assertEquals(600, ep.durationSeconds) // 00:10:00
    }

    @Test fun `parses chapters url when present`() {
        val result = parser.parse(xml)
        assertEquals("https://example.com/ep1.chapters.json", result.episodes[0].chaptersUrl)
    }

    @Test fun `chapters url null when absent`() {
        val result = parser.parse(xml)
        assertNull(result.episodes[1].chaptersUrl)
    }
}
```

**Step 3: Run — expect FAIL**

```bash
./gradlew :app:test --tests "com.podcastapp.data.network.RssParserTest"
```

**Step 4: Implement `RssParser.kt`**

```kotlin
package com.podcastapp.data.network

import android.util.Xml
import com.podcastapp.domain.model.Episode
import com.podcastapp.domain.model.Podcast
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale

data class ParsedFeed(val podcast: Podcast, val episodes: List<Episode>)

class RssParser {
    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)

    fun parse(xml: String): ParsedFeed {
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(xml))

        var podcastTitle = ""
        var podcastLink = ""
        var podcastDescription = ""
        var podcastAuthor = ""
        var podcastImage: String? = null
        val episodes = mutableListOf<Episode>()

        var inChannel = false
        var inItem = false
        var epTitle = ""
        var epAudioUrl = ""
        var epPubDate = 0L
        var epDurationSeconds = 0
        var epChaptersUrl: String? = null
        var epDescription = ""
        var currentText = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    currentText = ""
                    when {
                        parser.name == "channel" -> inChannel = true
                        parser.name == "item" -> { inItem = true; epTitle = ""; epAudioUrl = ""; epPubDate = 0L; epDurationSeconds = 0; epChaptersUrl = null; epDescription = "" }
                        parser.name == "enclosure" && inItem -> epAudioUrl = parser.getAttributeValue(null, "url") ?: ""
                        parser.name == "chapters" && inItem -> epChaptersUrl = parser.getAttributeValue(null, "url")
                    }
                }
                XmlPullParser.TEXT -> currentText = parser.text ?: ""
                XmlPullParser.END_TAG -> when {
                    parser.name == "item" -> {
                        episodes.add(Episode(
                            audioUrl = epAudioUrl,
                            podcastFeedUrl = podcastLink,
                            title = epTitle,
                            pubDate = epPubDate,
                            durationSeconds = epDurationSeconds,
                            chaptersUrl = epChaptersUrl
                        ))
                        inItem = false
                    }
                    inItem && parser.name == "title" -> epTitle = currentText
                    inItem && parser.name == "pubDate" -> epPubDate = runCatching { dateFormat.parse(currentText)?.time ?: 0L }.getOrDefault(0L)
                    inItem && parser.name == "duration" -> epDurationSeconds = parseDuration(currentText)
                    inItem && parser.name == "description" -> epDescription = currentText
                    inChannel && !inItem && parser.name == "title" -> podcastTitle = currentText
                    inChannel && !inItem && parser.name == "link" -> podcastLink = currentText
                    inChannel && !inItem && parser.name == "description" -> podcastDescription = currentText
                    inChannel && !inItem && parser.name == "author" -> podcastAuthor = currentText
                }
            }
            event = parser.next()
        }

        return ParsedFeed(
            podcast = Podcast(feedUrl = podcastLink, title = podcastTitle, author = podcastAuthor,
                description = podcastDescription, imageUrl = podcastImage),
            episodes = episodes
        )
    }

    private fun parseDuration(s: String): Int {
        val parts = s.trim().split(":").map { it.toIntOrNull() ?: 0 }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            else -> s.toIntOrNull() ?: 0
        }
    }
}
```

> **Note:** `android.util.Xml` is available in unit tests via the Android SDK stubs included with AGP. If tests fail with `RuntimeException: Stub!`, add `testOptions { unitTests.isReturnDefaultValues = true }` to `android {}` block in `app/build.gradle.kts`.

**Step 5: Run — expect PASS**

```bash
./gradlew :app:test --tests "com.podcastapp.data.network.RssParserTest"
```

**Step 6: Commit**

```bash
git add app/src/main/kotlin/com/podcastapp/data/network/RssParser.kt \
        app/src/test/kotlin/com/podcastapp/data/network/RssParserTest.kt \
        app/src/test/resources/test_feed.xml
git commit -m "feat: RSS parser with iTunes + Podcast Index namespace support"
```

---

## Task 3: Chapters JSON parser

**Files:**
- Create: `app/src/main/kotlin/com/podcastapp/data/network/ChaptersResponse.kt`
- Create: `app/src/test/kotlin/com/podcastapp/data/network/ChaptersParserTest.kt`

**Step 1: Write failing test**

`app/src/test/kotlin/com/podcastapp/data/network/ChaptersParserTest.kt`:
```kotlin
package com.podcastapp.data.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChaptersParserTest {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(ChaptersResponse::class.java)

    private val json = """
        {
          "version": "1.2.0",
          "chapters": [
            {"startTime": 0, "endTime": 20, "title": "Introduction"},
            {"startTime": 20, "endTime": 131, "title": "OpenAI News",
             "url": "https://example.com/article"}
          ]
        }
    """.trimIndent()

    @Test fun `parses two chapters`() {
        val response = adapter.fromJson(json)!!
        assertEquals(2, response.chapters.size)
    }

    @Test fun `parses chapter fields`() {
        val chapter = adapter.fromJson(json)!!.chapters[1]
        assertEquals(20, chapter.startTime)
        assertEquals(131, chapter.endTime)
        assertEquals("OpenAI News", chapter.title)
        assertEquals("https://example.com/article", chapter.url)
    }

    @Test fun `url nullable when absent`() {
        val chapter = adapter.fromJson(json)!!.chapters[0]
        assertNull(chapter.url)
    }

    @Test fun `converts to domain chapters with ms`() {
        val response = adapter.fromJson(json)!!
        val domain = response.toDomainChapters("http://ep.mp3")
        assertEquals(20_000L, domain[1].startTimeMs)
        assertEquals(131_000L, domain[1].endTimeMs)
    }
}
```

**Step 2: Run — expect FAIL**

```bash
./gradlew :app:test --tests "com.podcastapp.data.network.ChaptersParserTest"
```

**Step 3: Implement**

`app/src/main/kotlin/com/podcastapp/data/network/ChaptersResponse.kt`:
```kotlin
package com.podcastapp.data.network

import com.podcastapp.domain.model.Chapter
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ChaptersResponse(
    @Json(name = "version") val version: String = "",
    @Json(name = "chapters") val chapters: List<ChapterDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ChapterDto(
    @Json(name = "startTime") val startTime: Int = 0,
    @Json(name = "endTime") val endTime: Int = 0,
    @Json(name = "title") val title: String = "",
    @Json(name = "url") val url: String? = null
)

fun ChaptersResponse.toDomainChapters(episodeAudioUrl: String): List<Chapter> =
    chapters.map { dto ->
        Chapter(
            episodeAudioUrl = episodeAudioUrl,
            startTimeMs = dto.startTime * 1000L,
            endTimeMs = dto.endTime * 1000L,
            title = dto.title,
            url = dto.url
        )
    }
```

**Step 4: Run — expect PASS**

```bash
./gradlew :app:test --tests "com.podcastapp.data.network.ChaptersParserTest"
```

**Step 5: Commit**

```bash
git add app/src/main/kotlin/com/podcastapp/data/network/ChaptersResponse.kt \
        app/src/test/kotlin/com/podcastapp/data/network/ChaptersParserTest.kt
git commit -m "feat: chapters JSON parser with Moshi"
```

---

## Task 4: Room database

**Files:**
- Create: `app/src/main/kotlin/com/podcastapp/data/db/entities/` (3 entity files)
- Create: `app/src/main/kotlin/com/podcastapp/data/db/dao/` (3 DAO files)
- Create: `app/src/main/kotlin/com/podcastapp/data/db/PodcastDatabase.kt`

No unit tests here (Room DAOs require instrumented tests). Build verification suffices.

**Step 1: Create entities**

`app/src/main/kotlin/com/podcastapp/data/db/entities/PodcastEntity.kt`:
```kotlin
package com.podcastapp.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "podcasts")
data class PodcastEntity(
    @PrimaryKey val feedUrl: String,
    val title: String,
    val author: String,
    val description: String,
    val imageUrl: String?,
    val lastUpdated: Long
)
```

`app/src/main/kotlin/com/podcastapp/data/db/entities/EpisodeEntity.kt`:
```kotlin
package com.podcastapp.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "episodes")
data class EpisodeEntity(
    @PrimaryKey val audioUrl: String,
    val podcastFeedUrl: String,
    val title: String,
    val pubDate: Long,
    val durationSeconds: Int,
    val chaptersUrl: String?,
    val downloadPath: String?,
    val downloadStatus: String = "NONE"
)
```

`app/src/main/kotlin/com/podcastapp/data/db/entities/ChapterEntity.kt`:
```kotlin
package com.podcastapp.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val episodeAudioUrl: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val title: String,
    val url: String?
)
```

**Step 2: Create DAOs**

`app/src/main/kotlin/com/podcastapp/data/db/dao/PodcastDao.kt`:
```kotlin
package com.podcastapp.data.db.dao

import androidx.room.*
import com.podcastapp.data.db.entities.PodcastEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    @Query("SELECT * FROM podcasts ORDER BY lastUpdated DESC")
    fun getAll(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts WHERE feedUrl = :feedUrl")
    suspend fun getByUrl(feedUrl: String): PodcastEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(podcast: PodcastEntity)

    @Delete
    suspend fun delete(podcast: PodcastEntity)
}
```

`app/src/main/kotlin/com/podcastapp/data/db/dao/EpisodeDao.kt`:
```kotlin
package com.podcastapp.data.db.dao

import androidx.room.*
import com.podcastapp.data.db.entities.EpisodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE podcastFeedUrl = :feedUrl ORDER BY pubDate DESC")
    fun getForPodcast(feedUrl: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE audioUrl = :audioUrl")
    suspend fun getByAudioUrl(audioUrl: String): EpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(episodes: List<EpisodeEntity>)

    @Query("UPDATE episodes SET downloadPath = :path, downloadStatus = :status WHERE audioUrl = :audioUrl")
    suspend fun updateDownloadStatus(audioUrl: String, path: String?, status: String)
}
```

`app/src/main/kotlin/com/podcastapp/data/db/dao/ChapterDao.kt`:
```kotlin
package com.podcastapp.data.db.dao

import androidx.room.*
import com.podcastapp.data.db.entities.ChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE episodeAudioUrl = :audioUrl ORDER BY startTimeMs ASC")
    fun getForEpisode(audioUrl: String): Flow<List<ChapterEntity>>

    @Query("SELECT COUNT(*) FROM chapters WHERE episodeAudioUrl = :audioUrl")
    suspend fun countForEpisode(audioUrl: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<ChapterEntity>)
}
```

**Step 3: Create database**

`app/src/main/kotlin/com/podcastapp/data/db/PodcastDatabase.kt`:
```kotlin
package com.podcastapp.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.podcastapp.data.db.dao.ChapterDao
import com.podcastapp.data.db.dao.EpisodeDao
import com.podcastapp.data.db.dao.PodcastDao
import com.podcastapp.data.db.entities.ChapterEntity
import com.podcastapp.data.db.entities.EpisodeEntity
import com.podcastapp.data.db.entities.PodcastEntity

@Database(entities = [PodcastEntity::class, EpisodeEntity::class, ChapterEntity::class], version = 1)
abstract class PodcastDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun chapterDao(): ChapterDao
}
```

**Step 4: Create Hilt module**

`app/src/main/kotlin/com/podcastapp/data/di/DatabaseModule.kt`:
```kotlin
package com.podcastapp.data.di

import android.content.Context
import androidx.room.Room
import com.podcastapp.data.db.PodcastDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): PodcastDatabase =
        Room.databaseBuilder(ctx, PodcastDatabase::class.java, "podcast.db").build()

    @Provides fun providePodcastDao(db: PodcastDatabase) = db.podcastDao()
    @Provides fun provideEpisodeDao(db: PodcastDatabase) = db.episodeDao()
    @Provides fun provideChapterDao(db: PodcastDatabase) = db.chapterDao()
}
```

**Step 5: Verify project compiles**

```bash
./gradlew :app:kspDebugKotlin
```
Expected: `BUILD SUCCESSFUL`

**Step 6: Commit**

```bash
git add app/src/main/kotlin/com/podcastapp/data/
git commit -m "feat: Room database with Podcast, Episode, Chapter entities and DAOs"
```

---

## Task 5: Network + Repositories

**Files:**
- Create: `app/src/main/kotlin/com/podcastapp/data/di/NetworkModule.kt`
- Create: `app/src/main/kotlin/com/podcastapp/data/network/FeedApi.kt`
- Create: `app/src/main/kotlin/com/podcastapp/data/repository/PodcastRepository.kt`
- Create: `app/src/main/kotlin/com/podcastapp/data/repository/ChapterRepository.kt`
- Create: `app/src/test/kotlin/com/podcastapp/data/repository/PodcastRepositoryTest.kt`

**Step 1: Create NetworkModule**

`app/src/main/kotlin/com/podcastapp/data/di/NetworkModule.kt`:
```kotlin
package com.podcastapp.data.di

import com.podcastapp.data.network.FeedApi
import com.podcastapp.data.network.RssParser
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @Provides @Singleton
    fun provideFeedApi(okHttp: OkHttpClient): FeedApi = FeedApi(okHttp)

    @Provides @Singleton
    fun provideRssParser(): RssParser = RssParser()
}
```

> **Note:** Add `implementation("com.squareup.retrofit2:converter-scalars:2.11.0")` to `app/build.gradle.kts` if using Retrofit for raw string responses. Alternatively, use OkHttp directly (shown below).

**Step 2: Create FeedApi**

`app/src/main/kotlin/com/podcastapp/data/network/FeedApi.kt`:
```kotlin
package com.podcastapp.data.network

import okhttp3.OkHttpClient
import okhttp3.Request

class FeedApi(private val client: OkHttpClient) {
    suspend fun fetchXml(url: String): String {
        val request = Request.Builder().url(url).build()
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                response.body?.string() ?: throw Exception("Empty body")
            }
        }
    }

    suspend fun fetchChapters(url: String): ChaptersResponse {
        val json = fetchXml(url) // reuse — just fetches text
        val moshi = com.squareup.moshi.Moshi.Builder()
            .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
        return moshi.adapter(ChaptersResponse::class.java).fromJson(json)
            ?: throw Exception("Failed to parse chapters")
    }
}
```

**Step 3: Write repository test with MockWebServer**

`app/src/test/kotlin/com/podcastapp/data/repository/PodcastRepositoryTest.kt`:
```kotlin
package com.podcastapp.data.repository

import com.podcastapp.data.db.dao.EpisodeDao
import com.podcastapp.data.db.dao.PodcastDao
import com.podcastapp.data.network.FeedApi
import com.podcastapp.data.network.RssParser
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

class PodcastRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repo: PodcastRepository
    private val podcastDao = mockk<PodcastDao>(relaxed = true)
    private val episodeDao = mockk<EpisodeDao>(relaxed = true)

    @Before fun setUp() {
        server.start()
        val feedApi = FeedApi(OkHttpClient())
        repo = PodcastRepository(feedApi, RssParser(), podcastDao, episodeDao)
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun `addPodcast fetches feed and saves to db`() = runTest {
        server.enqueue(MockResponse().setBody(SAMPLE_XML).setResponseCode(200))
        val url = server.url("/feed.xml").toString()

        repo.addPodcast(url)

        coVerify { podcastDao.upsert(any()) }
        coVerify { episodeDao.upsertAll(any()) }
    }

    companion object {
        val SAMPLE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"
              xmlns:podcast="https://podcastindex.org/namespace/1.0">
              <channel>
                <title>Test</title><link>http://example.com</link><description>Test</description>
                <itunes:author>Author</itunes:author>
                <item>
                  <title>Ep 1</title><pubDate>Mon, 01 Jun 2026 08:00:00 +0000</pubDate>
                  <enclosure url="http://example.com/ep1.mp3" type="audio/mpeg" length="1000"/>
                  <itunes:duration>00:10:00</itunes:duration>
                  <guid isPermaLink="true">http://example.com/ep1.mp3</guid>
                </item>
              </channel>
            </rss>
        """.trimIndent()
    }
}
```

**Step 4: Run — expect FAIL**

```bash
./gradlew :app:test --tests "com.podcastapp.data.repository.PodcastRepositoryTest"
```

**Step 5: Implement repositories**

`app/src/main/kotlin/com/podcastapp/data/repository/PodcastRepository.kt`:
```kotlin
package com.podcastapp.data.repository

import com.podcastapp.data.db.dao.EpisodeDao
import com.podcastapp.data.db.dao.PodcastDao
import com.podcastapp.data.db.entities.EpisodeEntity
import com.podcastapp.data.db.entities.PodcastEntity
import com.podcastapp.data.network.FeedApi
import com.podcastapp.data.network.RssParser
import com.podcastapp.domain.model.DownloadStatus
import com.podcastapp.domain.model.Episode
import com.podcastapp.domain.model.Podcast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastRepository @Inject constructor(
    private val feedApi: FeedApi,
    private val rssParser: RssParser,
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao
) {
    val podcasts: Flow<List<Podcast>> = podcastDao.getAll().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun addPodcast(feedUrl: String) {
        val xml = feedApi.fetchXml(feedUrl)
        val parsed = rssParser.parse(xml)
        val podcast = parsed.podcast.copy(feedUrl = feedUrl, lastUpdated = System.currentTimeMillis())
        podcastDao.upsert(podcast.toEntity())
        episodeDao.upsertAll(parsed.episodes.map { it.toEntity(feedUrl) })
    }

    suspend fun refreshPodcast(feedUrl: String) = addPodcast(feedUrl)

    fun episodesForPodcast(feedUrl: String): Flow<List<Episode>> =
        episodeDao.getForPodcast(feedUrl).map { list -> list.map { it.toDomain() } }

    suspend fun removePodcast(feedUrl: String) {
        podcastDao.getByUrl(feedUrl)?.let { podcastDao.delete(it) }
    }
}

// Mapping extensions
fun PodcastEntity.toDomain() = Podcast(feedUrl, title, author, description, imageUrl, lastUpdated)
fun Podcast.toEntity() = PodcastEntity(feedUrl, title, author, description, imageUrl, lastUpdated)

fun EpisodeEntity.toDomain() = Episode(
    audioUrl = audioUrl, podcastFeedUrl = podcastFeedUrl, title = title,
    pubDate = pubDate, durationSeconds = durationSeconds, chaptersUrl = chaptersUrl,
    downloadPath = downloadPath, downloadStatus = DownloadStatus.valueOf(downloadStatus)
)
fun Episode.toEntity(feedUrl: String) = EpisodeEntity(
    audioUrl = audioUrl, podcastFeedUrl = feedUrl, title = title, pubDate = pubDate,
    durationSeconds = durationSeconds, chaptersUrl = chaptersUrl,
    downloadPath = downloadPath, downloadStatus = downloadStatus.name
)
```

`app/src/main/kotlin/com/podcastapp/data/repository/ChapterRepository.kt`:
```kotlin
package com.podcastapp.data.repository

import com.podcastapp.data.db.dao.ChapterDao
import com.podcastapp.data.db.entities.ChapterEntity
import com.podcastapp.data.network.FeedApi
import com.podcastapp.data.network.toDomainChapters
import com.podcastapp.domain.model.Chapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChapterRepository @Inject constructor(
    private val feedApi: FeedApi,
    private val chapterDao: ChapterDao
) {
    fun chaptersForEpisode(audioUrl: String): Flow<List<Chapter>> =
        chapterDao.getForEpisode(audioUrl).map { list ->
            list.map { it.toDomain() }
        }

    suspend fun fetchAndCacheChapters(audioUrl: String, chaptersUrl: String) {
        if (chapterDao.countForEpisode(audioUrl) > 0) return // already cached
        val response = feedApi.fetchChapters(chaptersUrl)
        val entities = response.toDomainChapters(audioUrl).map { it.toEntity() }
        chapterDao.insertAll(entities)
    }
}

fun ChapterEntity.toDomain() = Chapter(id, episodeAudioUrl, startTimeMs, endTimeMs, title, url)
fun Chapter.toEntity() = ChapterEntity(id, episodeAudioUrl, startTimeMs, endTimeMs, title, url)
```

**Step 6: Run test — expect PASS**

```bash
./gradlew :app:test --tests "com.podcastapp.data.repository.PodcastRepositoryTest"
```

**Step 7: Commit**

```bash
git add app/src/main/kotlin/com/podcastapp/data/ app/src/test/kotlin/com/podcastapp/data/repository/
git commit -m "feat: repositories with network fetch, RSS parsing, chapter caching"
```

---

## Task 6: PlaybackService

**Files:**
- Create: `app/src/main/kotlin/com/podcastapp/service/PlaybackService.kt`
- Create: `app/src/main/kotlin/com/podcastapp/service/ChapterNavigator.kt`
- Create: `app/src/test/kotlin/com/podcastapp/service/ChapterNavigatorTest.kt`

**Step 1: Write failing test for chapter navigation logic**

`app/src/test/kotlin/com/podcastapp/service/ChapterNavigatorTest.kt`:
```kotlin
package com.podcastapp.service

import com.podcastapp.domain.model.Chapter
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChapterNavigatorTest {
    private val chapters = listOf(
        Chapter(episodeAudioUrl = "u", startTimeMs = 0,    endTimeMs = 20_000,  title = "Intro", url = null),
        Chapter(episodeAudioUrl = "u", startTimeMs = 20_000, endTimeMs = 60_000, title = "Ch 2", url = "https://a.com"),
        Chapter(episodeAudioUrl = "u", startTimeMs = 60_000, endTimeMs = 120_000,title = "Ch 3", url = null)
    )

    @Test fun `current chapter at position 0ms is Intro`() {
        assertEquals("Intro", ChapterNavigator.currentChapter(chapters, 0L)?.title)
    }

    @Test fun `current chapter at 30s is Ch 2`() {
        assertEquals("Ch 2", ChapterNavigator.currentChapter(chapters, 30_000L)?.title)
    }

    @Test fun `next chapter from Intro is Ch 2`() {
        val next = ChapterNavigator.nextChapterStart(chapters, 5_000L)
        assertEquals(20_000L, next)
    }

    @Test fun `next chapter from last returns null`() {
        val next = ChapterNavigator.nextChapterStart(chapters, 90_000L)
        assertNull(next)
    }

    @Test fun `prev chapter from Ch 2 at 30s returns Intro start`() {
        val prev = ChapterNavigator.prevChapterStart(chapters, 30_000L)
        assertEquals(0L, prev)
    }

    @Test fun `prev chapter from near Ch 2 start seeks back to Ch 2 start`() {
        // Within 3s of chapter start → goes to previous chapter
        val prev = ChapterNavigator.prevChapterStart(chapters, 21_000L)
        assertEquals(0L, prev)
    }

    @Test fun `prev chapter further into Ch 2 seeks to Ch 2 start`() {
        val prev = ChapterNavigator.prevChapterStart(chapters, 40_000L)
        assertEquals(20_000L, prev)
    }
}
```

**Step 2: Run — expect FAIL**

```bash
./gradlew :app:test --tests "com.podcastapp.service.ChapterNavigatorTest"
```

**Step 3: Implement ChapterNavigator**

`app/src/main/kotlin/com/podcastapp/service/ChapterNavigator.kt`:
```kotlin
package com.podcastapp.service

import com.podcastapp.domain.model.Chapter

object ChapterNavigator {
    private const val PREV_THRESHOLD_MS = 3_000L

    fun currentChapter(chapters: List<Chapter>, positionMs: Long): Chapter? =
        chapters.lastOrNull { it.startTimeMs <= positionMs }

    fun nextChapterStart(chapters: List<Chapter>, positionMs: Long): Long? {
        val current = currentChapter(chapters, positionMs) ?: return null
        val idx = chapters.indexOf(current)
        return chapters.getOrNull(idx + 1)?.startTimeMs
    }

    fun prevChapterStart(chapters: List<Chapter>, positionMs: Long): Long? {
        val current = currentChapter(chapters, positionMs) ?: return null
        val idx = chapters.indexOf(current)
        val withinThreshold = (positionMs - current.startTimeMs) < PREV_THRESHOLD_MS
        return if (withinThreshold) {
            chapters.getOrNull(idx - 1)?.startTimeMs
        } else {
            current.startTimeMs
        }
    }
}
```

**Step 4: Run — expect PASS**

```bash
./gradlew :app:test --tests "com.podcastapp.service.ChapterNavigatorTest"
```

**Step 5: Implement PlaybackService**

`app/src/main/kotlin/com/podcastapp/service/PlaybackService.kt`:
```kotlin
package com.podcastapp.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.podcastapp.MainActivity
import com.podcastapp.domain.model.Chapter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    companion object {
        const val CMD_NEXT_CHAPTER = "com.podcastapp.NEXT_CHAPTER"
        const val CMD_PREV_CHAPTER = "com.podcastapp.PREV_CHAPTER"
    }

    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession
    var chapters: List<Chapter> = emptyList()

    private val callback = object : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_NEXT_CHAPTER, android.os.Bundle.EMPTY))
                .add(SessionCommand(CMD_PREV_CHAPTER, android.os.Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: android.os.Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_NEXT_CHAPTER -> {
                    ChapterNavigator.nextChapterStart(chapters, player.currentPosition)
                        ?.let { player.seekTo(it) }
                }
                CMD_PREV_CHAPTER -> {
                    ChapterNavigator.prevChapterStart(chapters, player.currentPosition)
                        ?.let { player.seekTo(it) }
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(MediaItem.Builder()
                .setMediaId("root").build(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            Futures.immediateFuture(LibraryResult.ofItemList(emptyList(), params))
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        val sessionActivity = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        mediaLibrarySession = MediaLibrarySession.Builder(this, player, callback)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaLibrarySession

    override fun onDestroy() {
        mediaLibrarySession.release()
        player.release()
        super.onDestroy()
    }
}
```

**Step 6: Verify compiles**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -10
```

**Step 7: Commit**

```bash
git add app/src/main/kotlin/com/podcastapp/service/ \
        app/src/test/kotlin/com/podcastapp/service/
git commit -m "feat: PlaybackService with MediaLibrarySession and chapter navigation"
```

---

## Task 7: ViewModels

**Files:**
- Create: `app/src/main/kotlin/com/podcastapp/ui/podcasts/PodcastListViewModel.kt`
- Create: `app/src/main/kotlin/com/podcastapp/ui/episodes/EpisodeListViewModel.kt`
- Create: `app/src/main/kotlin/com/podcastapp/ui/player/PlayerViewModel.kt`
- Create: `app/src/test/kotlin/com/podcastapp/ui/podcasts/PodcastListViewModelTest.kt`

**Step 1: Write failing ViewModel test**

`app/src/test/kotlin/com/podcastapp/ui/podcasts/PodcastListViewModelTest.kt`:
```kotlin
package com.podcastapp.ui.podcasts

import app.cash.turbine.test
import com.podcastapp.data.repository.PodcastRepository
import com.podcastapp.domain.model.Podcast
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastListViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val repo = mockk<PodcastRepository>(relaxed = true)

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `podcasts state emits from repo`() = runTest {
        val podcast = Podcast("http://feed.xml", "Test", "Author", "Desc", null)
        every { repo.podcasts } returns flowOf(listOf(podcast))
        val vm = PodcastListViewModel(repo)

        vm.podcasts.test {
            assertEquals(listOf(podcast), awaitItem())
        }
    }

    @Test fun `addPodcast calls repo`() = runTest {
        every { repo.podcasts } returns flowOf(emptyList())
        coEvery { repo.addPodcast(any()) } returns Unit
        val vm = PodcastListViewModel(repo)

        vm.addPodcast("http://feed.xml")

        coVerify { repo.addPodcast("http://feed.xml") }
    }

    @Test fun `addPodcast sets loading then clears`() = runTest {
        every { repo.podcasts } returns flowOf(emptyList())
        coEvery { repo.addPodcast(any()) } returns Unit
        val vm = PodcastListViewModel(repo)

        assertFalse(vm.isLoading.value)
        vm.addPodcast("http://feed.xml")
        assertFalse(vm.isLoading.value)
    }
}
```

**Step 2: Run — expect FAIL**

```bash
./gradlew :app:test --tests "com.podcastapp.ui.podcasts.PodcastListViewModelTest"
```

**Step 3: Implement ViewModels**

`app/src/main/kotlin/com/podcastapp/ui/podcasts/PodcastListViewModel.kt`:
```kotlin
package com.podcastapp.ui.podcasts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcastapp.data.repository.PodcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PodcastListViewModel @Inject constructor(
    private val repo: PodcastRepository
) : ViewModel() {
    val podcasts = repo.podcasts

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun addPodcast(url: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching { repo.addPodcast(url) }
                .onFailure { _error.value = it.message }
            _isLoading.value = false
        }
    }

    fun removePodcast(feedUrl: String) {
        viewModelScope.launch { repo.removePodcast(feedUrl) }
    }

    fun dismissError() { _error.value = null }
}
```

`app/src/main/kotlin/com/podcastapp/ui/episodes/EpisodeListViewModel.kt`:
```kotlin
package com.podcastapp.ui.episodes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcastapp.data.repository.PodcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EpisodeListViewModel @Inject constructor(
    private val repo: PodcastRepository,
    savedState: SavedStateHandle
) : ViewModel() {
    private val feedUrl: String = checkNotNull(savedState["feedUrl"])
    val episodes = repo.episodesForPodcast(feedUrl)

    fun refresh() {
        viewModelScope.launch { runCatching { repo.refreshPodcast(feedUrl) } }
    }
}
```

`app/src/main/kotlin/com/podcastapp/ui/player/PlayerViewModel.kt`:
```kotlin
package com.podcastapp.ui.player

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.podcastapp.data.repository.ChapterRepository
import com.podcastapp.domain.model.Chapter
import com.podcastapp.domain.model.Episode
import com.podcastapp.service.PlaybackService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chapterRepo: ChapterRepository
) : ViewModel() {

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()

    private val _currentChapterIndex = MutableStateFlow(0)
    val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()

    var controller: MediaController? = null
        private set

    fun connect() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = future.get()
        }, MoreExecutors.directExecutor())
    }

    fun playEpisode(episode: Episode) {
        viewModelScope.launch {
            episode.chaptersUrl?.let { url ->
                chapterRepo.fetchAndCacheChapters(episode.audioUrl, url)
            }
            chapterRepo.chaptersForEpisode(episode.audioUrl).collect { list ->
                _chapters.value = list
            }
        }
        val item = androidx.media3.common.MediaItem.Builder()
            .setMediaId(episode.audioUrl)
            .setUri(episode.audioUrl)
            .build()
        controller?.setMediaItem(item)
        controller?.prepare()
        controller?.play()
    }

    fun nextChapter() {
        controller?.sendCustomCommand(
            androidx.media3.session.SessionCommand(PlaybackService.CMD_NEXT_CHAPTER, android.os.Bundle.EMPTY),
            android.os.Bundle.EMPTY
        )
    }

    fun prevChapter() {
        controller?.sendCustomCommand(
            androidx.media3.session.SessionCommand(PlaybackService.CMD_PREV_CHAPTER, android.os.Bundle.EMPTY),
            android.os.Bundle.EMPTY
        )
    }

    fun seekForward30s() { controller?.seekTo((controller!!.currentPosition + 30_000).coerceAtMost(controller!!.duration)) }
    fun seekBack30s() { controller?.seekTo((controller!!.currentPosition - 30_000).coerceAtLeast(0)) }

    override fun onCleared() {
        controller?.release()
        super.onCleared()
    }
}
```

**Step 4: Run ViewModel test — expect PASS**

```bash
./gradlew :app:test --tests "com.podcastapp.ui.podcasts.PodcastListViewModelTest"
```

**Step 5: Commit**

```bash
git add app/src/main/kotlin/com/podcastapp/ui/ \
        app/src/test/kotlin/com/podcastapp/ui/
git commit -m "feat: ViewModels for podcast list, episode list, and player"
```

---

## Task 8: UI — PodcastListScreen

**Files:**
- Create: `app/src/main/kotlin/com/podcastapp/ui/podcasts/PodcastListScreen.kt`

**Step 1: Implement screen**

```kotlin
package com.podcastapp.ui.podcasts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.podcastapp.domain.model.Podcast

@Composable
fun PodcastListScreen(
    onPodcastClick: (String) -> Unit,
    vm: PodcastListViewModel = hiltViewModel()
) {
    val podcasts by vm.podcasts.collectAsStateWithLifecycle(emptyList())
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Podcasts") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add podcast")
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(contentPadding = padding) {
                items(podcasts, key = { it.feedUrl }) { podcast ->
                    PodcastRow(podcast = podcast, onClick = { onPodcastClick(podcast.feedUrl) })
                    HorizontalDivider()
                }
            }
        }
        error?.let { msg ->
            AlertDialog(
                onDismissRequest = { vm.dismissError() },
                text = { Text(msg) },
                confirmButton = { TextButton(onClick = { vm.dismissError() }) { Text("OK") } }
            )
        }
    }

    if (showAddDialog) {
        AddPodcastDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { url -> vm.addPodcast(url); showAddDialog = false }
        )
    }
}

@Composable
private fun PodcastRow(podcast: Podcast, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = podcast.imageUrl,
            contentDescription = null,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(podcast.title, style = MaterialTheme.typography.titleMedium)
            Text(podcast.author, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AddPodcastDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Podcast") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("RSS Feed URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (url.isNotBlank()) onAdd(url) }, enabled = url.isNotBlank()) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
```

**Step 2: Compile check**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -5
```

**Step 3: Commit**

```bash
git add app/src/main/kotlin/com/podcastapp/ui/podcasts/
git commit -m "feat: PodcastListScreen with add podcast dialog"
```

---

## Task 9: UI — EpisodeListScreen

**Files:**
- Create: `app/src/main/kotlin/com/podcastapp/ui/episodes/EpisodeListScreen.kt`

**Step 1: Implement screen**

```kotlin
package com.podcastapp.ui.episodes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.podcastapp.domain.model.Episode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EpisodeListScreen(
    onBack: () -> Unit,
    onEpisodeClick: (String) -> Unit,  // audioUrl
    vm: EpisodeListViewModel = hiltViewModel()
) {
    val episodes by vm.episodes.collectAsStateWithLifecycle(emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Episodes") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            items(episodes, key = { it.audioUrl }) { episode ->
                EpisodeRow(episode = episode, onClick = { onEpisodeClick(episode.audioUrl) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp)
    ) {
        Text(episode.title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "${formatDate(episode.pubDate)} · ${formatDuration(episode.durationSeconds)}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
private fun formatDate(millis: Long) = if (millis > 0) dateFmt.format(Date(millis)) else ""
private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
```

**Step 2: Compile check**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -5
```

**Step 3: Commit**

```bash
git add app/src/main/kotlin/com/podcastapp/ui/episodes/
git commit -m "feat: EpisodeListScreen"
```

---

## Task 10: UI — PlayerScreen

**Files:**
- Create: `app/src/main/kotlin/com/podcastapp/ui/player/PlayerScreen.kt`

**Step 1: Implement screen**

```kotlin
package com.podcastapp.ui.player

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.podcastapp.domain.model.Chapter

@Composable
fun PlayerScreen(
    audioUrl: String,
    onBack: () -> Unit,
    vm: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val chapters by vm.chapters.collectAsStateWithLifecycle()
    val currentIdx by vm.currentChapterIndex.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        vm.connect()
        onDispose {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(chapters.getOrNull(currentIdx)?.title ?: "Playing") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            // Playback controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                IconButton(onClick = { vm.prevChapter() }) {
                    Icon(Icons.Filled.SkipPrevious, "Previous chapter", Modifier.size(40.dp))
                }
                IconButton(onClick = { vm.seekBack30s() }) {
                    Icon(Icons.Filled.Replay30, "-30s", Modifier.size(36.dp))
                }
                IconButton(
                    onClick = {
                        val c = vm.controller
                        if (c?.isPlaying == true) c.pause() else c?.play()
                    },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(Icons.Filled.PlayCircle, "Play/Pause", Modifier.size(56.dp))
                }
                IconButton(onClick = { vm.seekForward30s() }) {
                    Icon(Icons.Filled.Forward30, "+30s", Modifier.size(36.dp))
                }
                IconButton(onClick = { vm.nextChapter() }) {
                    Icon(Icons.Filled.SkipNext, "Next chapter", Modifier.size(40.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // Current chapter link actions
            chapters.getOrNull(currentIdx)?.url?.let { url ->
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = {
                        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
                    }) { Text("Open") }
                    OutlinedButton(onClick = {
                        context.startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url)
                            }, "Share link"
                        ))
                    }) { Text("Share") }
                }
                Spacer(Modifier.height(12.dp))
            }

            HorizontalDivider()
            Text("Chapters", style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(8.dp).align(Alignment.Start))

            // Chapter list
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(chapters) { idx, chapter ->
                    ChapterRow(
                        chapter = chapter,
                        isActive = idx == currentIdx,
                        onClick = { vm.controller?.seekTo(chapter.startTimeMs) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ChapterRow(chapter: Chapter, isActive: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            formatMs(chapter.startTimeMs),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(52.dp)
        )
        Text(chapter.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (chapter.url != null) {
            Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

private fun formatMs(ms: Long): String {
    val s = ms / 1000; val m = s / 60; val h = m / 60
    return if (h > 0) "%d:%02d:%02d".format(h, m % 60, s % 60)
    else "%d:%02d".format(m, s % 60)
}
```

> **Note:** Add Chrome Custom Tabs dependency: `implementation("androidx.browser:browser:1.8.0")` in `app/build.gradle.kts`.

**Step 2: Compile check**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -5
```

**Step 3: Commit**

```bash
git add app/src/main/kotlin/com/podcastapp/ui/player/
git commit -m "feat: PlayerScreen with chapter list, controls, and link actions"
```

---

## Task 11: Navigation graph

**Files:**
- Modify: `app/src/main/kotlin/com/podcastapp/ui/PodcastNavGraph.kt`

**Step 1: Wire up navigation**

```kotlin
package com.podcastapp.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.podcastapp.ui.episodes.EpisodeListScreen
import com.podcastapp.ui.player.PlayerScreen
import com.podcastapp.ui.podcasts.PodcastListScreen
import java.net.URLDecoder
import java.net.URLEncoder

@Composable
fun PodcastNavGraph() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "podcasts") {
        composable("podcasts") {
            PodcastListScreen(onPodcastClick = { feedUrl ->
                nav.navigate("episodes/${URLEncoder.encode(feedUrl, "UTF-8")}")
            })
        }
        composable(
            "episodes/{feedUrl}",
            arguments = listOf(navArgument("feedUrl") { type = NavType.StringType })
        ) {
            EpisodeListScreen(
                onBack = { nav.popBackStack() },
                onEpisodeClick = { audioUrl ->
                    nav.navigate("player/${URLEncoder.encode(audioUrl, "UTF-8")}")
                }
            )
        }
        composable(
            "player/{audioUrl}",
            arguments = listOf(navArgument("audioUrl") { type = NavType.StringType })
        ) { backStack ->
            val audioUrl = URLDecoder.decode(backStack.arguments?.getString("audioUrl") ?: "", "UTF-8")
            PlayerScreen(audioUrl = audioUrl, onBack = { nav.popBackStack() })
        }
    }
}
```

**Step 2: Compile and test on emulator**

```bash
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Manually test: add sample feed URL `https://keithfry.github.io/web-pages/techradar/AI/podcast.xml`, navigate to episodes, open player, verify chapter list appears.

**Step 3: Commit**

```bash
git add app/src/main/kotlin/com/podcastapp/ui/PodcastNavGraph.kt
git commit -m "feat: navigation graph wiring all three screens"
```

---

## Task 12: Voice — SpeechRecognizer (Tier 2)

**Files:**
- Create: `app/src/main/kotlin/com/podcastapp/ui/player/VoiceCommandHandler.kt`
- Modify: `app/src/main/kotlin/com/podcastapp/ui/player/PlayerScreen.kt` (add mic FAB)
- Create: `app/src/test/kotlin/com/podcastapp/ui/player/VoiceCommandHandlerTest.kt`

**Step 1: Write failing test**

`app/src/test/kotlin/com/podcastapp/ui/player/VoiceCommandHandlerTest.kt`:
```kotlin
package com.podcastapp.ui.player

import org.junit.Test
import kotlin.test.assertEquals

class VoiceCommandHandlerTest {
    @Test fun `'next section' maps to NEXT_CHAPTER`() =
        assertEquals(VoiceCommand.NEXT_CHAPTER, VoiceCommandHandler.parse("next section"))

    @Test fun `'skip' maps to NEXT_CHAPTER`() =
        assertEquals(VoiceCommand.NEXT_CHAPTER, VoiceCommandHandler.parse("skip"))

    @Test fun `'previous section' maps to PREV_CHAPTER`() =
        assertEquals(VoiceCommand.PREV_CHAPTER, VoiceCommandHandler.parse("previous section"))

    @Test fun `'fast forward' maps to SEEK_FORWARD`() =
        assertEquals(VoiceCommand.SEEK_FORWARD, VoiceCommandHandler.parse("fast forward"))

    @Test fun `'rewind' maps to SEEK_BACK`() =
        assertEquals(VoiceCommand.SEEK_BACK, VoiceCommandHandler.parse("rewind"))

    @Test fun `'open link' maps to OPEN_LINK`() =
        assertEquals(VoiceCommand.OPEN_LINK, VoiceCommandHandler.parse("open link"))

    @Test fun `'save link' maps to SHARE_LINK`() =
        assertEquals(VoiceCommand.SHARE_LINK, VoiceCommandHandler.parse("save link"))

    @Test fun `unknown command returns null`() =
        assertEquals(null, VoiceCommandHandler.parse("what is the weather"))
}
```

**Step 2: Run — expect FAIL**

```bash
./gradlew :app:test --tests "com.podcastapp.ui.player.VoiceCommandHandlerTest"
```

**Step 3: Implement**

`app/src/main/kotlin/com/podcastapp/ui/player/VoiceCommandHandler.kt`:
```kotlin
package com.podcastapp.ui.player

enum class VoiceCommand { NEXT_CHAPTER, PREV_CHAPTER, SEEK_FORWARD, SEEK_BACK, OPEN_LINK, SHARE_LINK }

object VoiceCommandHandler {
    fun parse(input: String): VoiceCommand? = when (input.trim().lowercase()) {
        "next", "next section", "skip", "forward" -> VoiceCommand.NEXT_CHAPTER
        "back", "previous", "previous section", "go back" -> VoiceCommand.PREV_CHAPTER
        "fast forward", "skip forward" -> VoiceCommand.SEEK_FORWARD
        "rewind", "skip back", "go back 30" -> VoiceCommand.SEEK_BACK
        "open link", "open", "open article" -> VoiceCommand.OPEN_LINK
        "save link", "save", "add to list", "share link" -> VoiceCommand.SHARE_LINK
        else -> null
    }
}
```

**Step 4: Run — expect PASS**

```bash
./gradlew :app:test --tests "com.podcastapp.ui.player.VoiceCommandHandlerTest"
```

**Step 5: Add mic FAB to PlayerScreen**

Add to the `Scaffold` in `PlayerScreen.kt`:
```kotlin
floatingActionButton = {
    FloatingActionButton(onClick = { startVoiceInput(context) { result ->
        when (VoiceCommandHandler.parse(result)) {
            VoiceCommand.NEXT_CHAPTER -> vm.nextChapter()
            VoiceCommand.PREV_CHAPTER -> vm.prevChapter()
            VoiceCommand.SEEK_FORWARD -> vm.seekForward30s()
            VoiceCommand.SEEK_BACK -> vm.seekBack30s()
            VoiceCommand.OPEN_LINK -> { /* trigger open */ }
            VoiceCommand.SHARE_LINK -> { /* trigger share */ }
            null -> {}
        }
    }}) {
        Icon(Icons.Filled.Mic, "Voice command")
    }
}
```

Add helper function (outside Composable, same file):
```kotlin
private fun startVoiceInput(context: android.content.Context, onResult: (String) -> Unit) {
    val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Say a command")
    }
    // Launch via Activity result — wire up in MainActivity or use rememberLauncherForActivityResult
    // See Step 5 note below
}
```

> **Note on voice result:** `SpeechRecognizer` requires either `startActivityForResult` (simple) or `SpeechRecognizer.createSpeechRecognizer` (in-process). The simplest approach: use `rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult())` in the Composable and handle the result there. Pass the recognized string to `VoiceCommandHandler.parse`.

Full mic FAB with launcher:
```kotlin
val voiceLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    val matches = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
    val best = matches?.firstOrNull() ?: return@rememberLauncherForActivityResult
    when (VoiceCommandHandler.parse(best)) {
        VoiceCommand.NEXT_CHAPTER -> vm.nextChapter()
        VoiceCommand.PREV_CHAPTER -> vm.prevChapter()
        VoiceCommand.SEEK_FORWARD -> vm.seekForward30s()
        VoiceCommand.SEEK_BACK -> vm.seekBack30s()
        VoiceCommand.OPEN_LINK -> chapters.getOrNull(currentIdx)?.url?.let {
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(it))
        }
        VoiceCommand.SHARE_LINK -> chapters.getOrNull(currentIdx)?.url?.let { url ->
            context.startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url) },
                "Share link"
            ))
        }
        null -> {}
    }
}
// In FAB:
FloatingActionButton(onClick = {
    voiceLauncher.launch(Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    })
}) { Icon(Icons.Filled.Mic, "Voice command") }
```

**Step 6: Run all tests**

```bash
./gradlew :app:test
```
Expected: all pass.

**Step 7: Commit**

```bash
git add app/src/main/kotlin/com/podcastapp/ui/player/ \
        app/src/test/kotlin/com/podcastapp/ui/player/
git commit -m "feat: voice command handler and mic FAB on player screen"
```

---

## Task 13: Downloads via WorkManager

**Files:**
- Create: `app/src/main/kotlin/com/podcastapp/data/download/DownloadWorker.kt`
- Modify: `app/src/main/kotlin/com/podcastapp/data/repository/PodcastRepository.kt` (add `downloadEpisode`)

**Step 1: Create DownloadWorker**

```kotlin
package com.podcastapp.data.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.podcastapp.data.db.dao.EpisodeDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val episodeDao: EpisodeDao,
    private val okHttp: OkHttpClient
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val audioUrl = inputData.getString(KEY_AUDIO_URL) ?: return Result.failure()
        episodeDao.updateDownloadStatus(audioUrl, null, "DOWNLOADING")
        return try {
            val request = Request.Builder().url(audioUrl).build()
            val response = okHttp.newCall(request).execute()
            if (!response.isSuccessful) {
                episodeDao.updateDownloadStatus(audioUrl, null, "NONE")
                return Result.retry()
            }
            val file = File(applicationContext.filesDir, "${audioUrl.hashCode()}.mp3")
            response.body?.byteStream()?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            episodeDao.updateDownloadStatus(audioUrl, file.absolutePath, "DONE")
            Result.success()
        } catch (e: Exception) {
            episodeDao.updateDownloadStatus(audioUrl, null, "NONE")
            Result.retry()
        }
    }

    companion object {
        const val KEY_AUDIO_URL = "audio_url"

        fun buildRequest(audioUrl: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(KEY_AUDIO_URL to audioUrl))
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
    }
}
```

**Step 2: Add `downloadEpisode` to PodcastRepository**

Add import and WorkManager injection, then:
```kotlin
fun downloadEpisode(audioUrl: String) {
    workManager.enqueueUniqueWork(
        "download_$audioUrl",
        ExistingWorkPolicy.KEEP,
        DownloadWorker.buildRequest(audioUrl)
    )
}
```

Add `workManager: WorkManager` to constructor and `@Inject` it via a Hilt module:
```kotlin
// In DatabaseModule or a new WorkModule:
@Provides @Singleton
fun provideWorkManager(@ApplicationContext ctx: Context): WorkManager =
    WorkManager.getInstance(ctx)
```

**Step 3: Add download button to EpisodeListScreen**

In `EpisodeRow`, add a trailing icon button:
```kotlin
Row(...) {
    Column(Modifier.weight(1f)) { /* title + date */ }
    if (episode.downloadStatus == DownloadStatus.NONE) {
        IconButton(onClick = onDownload) {
            Icon(Icons.Filled.Download, "Download")
        }
    } else if (episode.downloadStatus == DownloadStatus.DONE) {
        Icon(Icons.Filled.DownloadDone, "Downloaded", Modifier.padding(12.dp))
    } else {
        CircularProgressIndicator(Modifier.size(24.dp).padding(12.dp))
    }
}
```

Pass `onDownload: () -> Unit` from `EpisodeListScreen` → call `vm.downloadEpisode(episode.audioUrl)`.

**Step 4: Compile check**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -5
```

**Step 5: Commit**

```bash
git add app/src/main/kotlin/com/podcastapp/data/download/ \
        app/src/main/kotlin/com/podcastapp/data/repository/ \
        app/src/main/kotlin/com/podcastapp/ui/episodes/
git commit -m "feat: episode download via WorkManager with progress state"
```

---

## Task 14: Final integration test

**Step 1: Run all unit tests**

```bash
./gradlew :app:test
```
Expected: all pass.

**Step 2: Install on emulator/device and run manual verification checklist**

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Run through this checklist manually:

- [ ] Open app → Podcast List is empty
- [ ] Tap + → enter `https://keithfry.github.io/web-pages/techradar/AI/podcast.xml` → tap Add
- [ ] Podcast appears in list with title "AI & Robotics Daily Radar"
- [ ] Tap podcast → episode list shows dated episodes
- [ ] Tap episode → player screen opens
- [ ] Chapter list appears (30+ chapters for June 1 episode)
- [ ] Tap Play → audio begins streaming
- [ ] Tap ▶| → jumps to next chapter
- [ ] Tap |◀ → jumps back to previous chapter start
- [ ] Tap 30▶ → seeks forward 30 seconds
- [ ] "Open" button visible on chapters with URLs → tap → Chrome Custom Tab opens
- [ ] "Share" button → share sheet appears
- [ ] Minimize app → audio continues in notification
- [ ] Tap mic → system speech input appears → say "next section" → chapter advances
- [ ] "Ok Google, skip forward" → audio skips

**Step 3: Tag release**

```bash
git tag v0.1.0-alpha
```

---

## What's next (Phase 2)

- Android Auto: implement `MediaLibrarySession.Callback.onGetChildren` browse tree + `CommandButton`s
- Always-on wake word: Picovoice Porcupine integration
- Pull-to-refresh on episode list
- Podcast image in player (episode artwork from feed `<itunes:image>`)
- Playback speed control
- Sleep timer
