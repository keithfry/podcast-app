# Read-Along Implementation

## Context

Display synchronized transcript text during episode playback. Active segment highlights and auto-scrolls. Tap to seek. The generator (web-pages) exports a `.transcript.json` sidecar per episode and exposes it via `<podcast:transcript>` in the RSS feed — see `web-pages/docs/plans/2026-06-14-transcript-export-implementation.md`. Because audio is generated from known LLM-written scripts, transcripts are exact; no Whisper API is needed for paragraph-level sync.

High-level design already exists at `docs/plans/2026-06-06-read-along-design.md`. This plan is the concrete implementation spec.

---

## Transcript JSON Format (from generator)

```json
{
  "version": "1.0.0",
  "segments": [
    { "startTime": 0,  "endTime": 20,  "text": "Welcome to AI Daily Radar...", "voice": "af_heart" },
    { "startTime": 20, "endTime": 131, "text": "From TechCrunch, \"...\". ...", "voice": "am_echo"  }
  ]
}
```

`startTime` / `endTime` in integer seconds. Multiply × 1000 for milliseconds.

---

## Touch Points (in implementation order)

### 1. `domain/model/Episode.kt`

Add after `chaptersUrl`:

```kotlin
val transcriptUrl: String? = null,
```

### 2. `data/db/entities/EpisodeEntity.kt`

Add after `chaptersUrl`:

```kotlin
val transcriptUrl: String? = null,
```

### 3. `data/db/PodcastDatabase.kt`

Current version = 3. Bump to 4. Add:

```kotlin
val MIGRATION_3_4 = Migration(3, 4) { db ->
    db.execSQL("ALTER TABLE episodes ADD COLUMN transcript_url TEXT")
}
```

Register in `Room.databaseBuilder(...)`:

```kotlin
.addMigrations(/* existing migrations */, MIGRATION_3_4)
```

### 4. `data/network/RssParser.kt`

Namespace constant already at line 16: `NS_PODCAST = "https://podcastindex.org/namespace/1.0"`.
Chapters parsed at line 58:
```kotlin
parser.namespace == NS_PODCAST && parser.name == "chapters" -> if (inItem) epChaptersUrl = parser.getAttributeValue(null, "url")
```

Add `var epTranscriptUrl: String? = null` alongside `epChaptersUrl`. Add parse branch:

```kotlin
parser.namespace == NS_PODCAST && parser.name == "transcript" -> if (inItem) epTranscriptUrl = parser.getAttributeValue(null, "url")
```

Reset to `null` when entering each new `<item>`. Pass `transcriptUrl = epTranscriptUrl` to `Episode(...)` constructor.

### 5. New `domain/model/TranscriptSegment.kt`

```kotlin
package com.frybynite.podlore.domain.model

data class TranscriptSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val voice: String,
)
```

### 6. New `data/repository/TranscriptRepository.kt`

Follow `ChapterRepository` pattern (lines 16–41). Use `org.json.JSONObject` (already on Android) — no new dependency needed.

```kotlin
@Singleton
class TranscriptRepository @Inject constructor(
    private val feedApi: FeedApi,
    @ApplicationContext private val context: Context,
) {
    private val cacheDir = File(context.filesDir, "transcripts").also { it.mkdirs() }

    suspend fun getTranscript(
        audioUrl: String,
        transcriptUrl: String,
    ): List<TranscriptSegment>? = withContext(Dispatchers.IO) {
        val cacheFile = File(cacheDir, "${audioUrl.hashCode()}.transcript.json")
        val json = if (cacheFile.exists()) cacheFile.readText()
                   else feedApi.fetchRaw(transcriptUrl).also { cacheFile.writeText(it) }
        parseTranscript(json)
    }

    private fun parseTranscript(json: String): List<TranscriptSegment>? = runCatching {
        val arr = JSONObject(json).getJSONArray("segments")
        List(arr.length()) { i ->
            val seg = arr.getJSONObject(i)
            TranscriptSegment(
                startMs = seg.getLong("startTime") * 1000L,
                endMs   = seg.getLong("endTime")   * 1000L,
                text    = seg.getString("text"),
                voice   = seg.optString("voice", ""),
            )
        }
    }.getOrNull()
}
```

Check `FeedApi` for a `fetchRaw(url: String): String` method. If absent, add:

```kotlin
@GET
suspend fun fetchRaw(@Url url: String): String
```

Provide in `DeepDiveModule.kt`:

```kotlin
@Provides @Singleton
fun provideTranscriptRepository(
    feedApi: FeedApi,
    @ApplicationContext context: Context,
): TranscriptRepository = TranscriptRepository(feedApi, context)
```

### 7. `ui/player/PlayerViewModel.kt`

Add sealed state (same file, alongside `DeepDiveState` at line 589):

```kotlin
sealed class TranscriptUiState {
    data object Idle    : TranscriptUiState()
    data object Loading : TranscriptUiState()
    data class  Ready(val segments: List<TranscriptSegment>) : TranscriptUiState()
    data class  Error(val message: String) : TranscriptUiState()
}
```

Add to `PlayerViewModel` body:

```kotlin
private val _transcriptState = MutableStateFlow<TranscriptUiState>(TranscriptUiState.Idle)
val transcriptState: StateFlow<TranscriptUiState> = _transcriptState.asStateFlow()

val activeSegmentIndex: StateFlow<Int> = combine(_transcriptState, _currentPositionMs) { t, pos ->
    if (t !is TranscriptUiState.Ready) -1
    else t.segments.indexOfLast { it.startMs <= pos }
}.stateIn(viewModelScope, SharingStarted.Lazily, -1)

fun loadTranscript() {
    val url      = _currentEpisode.value?.transcriptUrl ?: return
    val audioUrl = _currentEpisode.value?.audioUrl      ?: return
    if (_transcriptState.value is TranscriptUiState.Ready) return
    viewModelScope.launch {
        _transcriptState.value = TranscriptUiState.Loading
        val segments = transcriptRepository.getTranscript(audioUrl, url)
        _transcriptState.value = if (segments != null) TranscriptUiState.Ready(segments)
                                  else TranscriptUiState.Error("Failed to load transcript")
    }
}
```

Inject `TranscriptRepository` in the constructor.

Also reset state when episode changes:

```kotlin
// In the episode-change observer (wherever _currentEpisode is updated):
_transcriptState.value = TranscriptUiState.Idle
```

### 8. New `ui/player/ReadAlongSheet.kt`

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadAlongSheet(
    transcriptState: TranscriptUiState,
    activeIndex: Int,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        when (transcriptState) {
            is TranscriptUiState.Loading -> {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is TranscriptUiState.Error -> {
                Text(transcriptState.message, Modifier.padding(16.dp))
            }
            is TranscriptUiState.Ready -> {
                val listState = rememberLazyListState()
                LaunchedEffect(activeIndex) {
                    if (activeIndex >= 0) listState.animateScrollToItem(activeIndex)
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxHeight(0.85f)) {
                    itemsIndexed(transcriptState.segments) { i, seg ->
                        val isActive = i == activeIndex
                        Surface(
                            color = if (isActive) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSeek(seg.startMs) },
                        ) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                Text(
                                    voiceLabel(seg.voice),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(seg.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
            else -> {}
        }
    }
}

private fun voiceLabel(voice: String) = when (voice) {
    "af_heart"   -> "Host"
    "am_echo"    -> "Reporter"
    "af_bella"   -> "Reporter"
    "am_michael" -> "Reporter"
    "af_nova"    -> "Reporter"
    else         -> voice
}
```

### 9. `ui/player/PlayerScreen.kt`

Add after the chapter list composable call. The Deep Dive "More" button is in the swipe-reveal panel at line 411; the "Read Along" button goes below the chapter list (around line 488+) as a standalone `TextButton`.

```kotlin
var showReadAlong by remember { mutableStateOf(false) }
val transcriptState by vm.transcriptState.collectAsStateWithLifecycle()
val activeSegmentIndex by vm.activeSegmentIndex.collectAsStateWithLifecycle()

if (episode?.transcriptUrl != null) {
    TextButton(
        onClick = {
            vm.loadTranscript()
            showReadAlong = true
        },
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Icon(Icons.Outlined.Subtitles, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Read Along")
    }
}

if (showReadAlong) {
    ReadAlongSheet(
        transcriptState    = transcriptState,
        activeIndex        = activeSegmentIndex,
        onSeek             = { ms -> vm.seekTo(ms) },
        onDismiss          = { showReadAlong = false },
    )
}
```

---

## Word-Level Sync (Phase 2 — defer)

Skip for now. Paragraph sync is exact because the generator owns the script text. Word-level Whisper (~$0.08/episode) adds cost and complexity with no immediate need.

---

## Verification

1. Subscribe to AI Daily Radar feed (needs updated RSS from web-pages changes)
2. Open any episode in the player
3. Confirm "Read Along" button appears below chapter list
4. Tap "Read Along" — sheet opens with loading state, then segments render
5. Voice label "Host" appears on intro segment; "Reporter" on item segments
6. Play audio — active segment highlights and auto-scrolls each second
7. Tap any segment — player seeks to that timestamp
8. DB migration: fresh install → no crash; upgrade from v3 → v4 retains episode data
