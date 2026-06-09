# Read-Along / Synchronized Transcript

Display synchronized transcript text during podcast playback. Active segment highlighted and scrolled into view. Tap to seek.

## Two-tier approach

1. **Paragraph sync (free, offline)** — use `transcript.json` exported by the generator. Exact text, exact segment boundaries. No API cost, works without network.
2. **Word sync (on-demand)** — run Groq Whisper on the MP3 to get per-word timestamps. Cached after first run.

Start with paragraph sync only. Word sync is additive.

## Data source

Each RSS item exposes:
```xml
<podcast:transcript url="https://.../ai-radar-2026-06-06.transcript.json" type="application/json"/>
```

Format:
```json
{
  "version": "1.0.0",
  "segments": [
    { "startTime": 0, "endTime": 47, "text": "Welcome to AI Daily Radar...", "voice": "af_heart" },
    { "startTime": 47, "endTime": 158, "text": "From TechCrunch...", "voice": "am_echo" }
  ]
}
```

`startTime` / `endTime` in integer seconds. One segment per podcast chapter/item.

## Architecture

### New files

**`TranscriptRepository`**
- Download `transcript.json` URL (from RSS `<podcast:transcript>` tag or derived from MP3 URL)
- Cache to `context.filesDir/transcripts/<episode_id>.transcript.json`
- Parse into `List<TranscriptSegment>`
- Expose `suspend fun getTranscript(episodeAudioUrl: String, transcriptUrl: String): List<TranscriptSegment>?`

**`TranscriptSegment`** (data class)
```kotlin
data class TranscriptSegment(
    val startMs: Long,   // startTime * 1000
    val endMs: Long,
    val text: String,
    val voice: String,
)
```

**`ReadAlongSheet`** (Composable bottom sheet)
- `LazyColumn` of segment cards
- Active segment: accent background, auto-scrolled to center
- Tap segment → `player.seekTo(segment.startMs)`
- Shows voice label per segment (e.g. "af_heart" mapped to display name)

### ViewModel changes (`PlayerViewModel`)

```kotlin
val transcriptState: StateFlow<TranscriptUiState>  // Idle | Loading | Ready(segments) | Error

fun loadTranscript()  // triggered when sheet opens
```

Active segment index derived from `playerPosition`:
```kotlin
val activeSegmentIndex: StateFlow<Int> = combine(transcriptState, playerPosition) { transcript, pos ->
    if (transcript !is TranscriptUiState.Ready) return@combine -1
    transcript.segments.indexOfLast { it.startMs <= pos }
}.stateIn(...)
```

### RSS parsing

Add `transcriptUrl: String?` field to the existing episode/item data model. Parse from `<podcast:transcript url="...">` in `RssFeedParser`. Store in Room `Episode` table (new nullable column, migration required).

### Entry point

"Read along" button in `PlayerScreen` below the chapter list, visible only when transcript is available (`episode.transcriptUrl != null`). Opens `ReadAlongSheet` as a `ModalBottomSheet`.

## Word-level sync (phase 2)

When user enables "word highlight" in the sheet:
1. Call Groq Whisper API (`whisper-large-v3-turbo`) with `response_format=verbose_json`, `timestamp_granularities[]=word`, and `prompt=<first segment text>` for accuracy priming.
2. Parse `words[].{word, start, end}` from response. Cache as `<episode_id>.words.json`.
3. Replace segment-level highlighting with per-word span highlighting using `AnnotatedString` + `BasicText`.

Cost: ~$0.004/min. A 20-min episode ≈ $0.08. Run once, cached.

## Room migration

```sql
ALTER TABLE episodes ADD COLUMN transcript_url TEXT;
```

Version bump: `Episode` schema version + migration in `PodcastDatabase`.

## Touch points

| File | Change |
|------|--------|
| `RssFeedParser.kt` | Parse `<podcast:transcript>` tag |
| `Episode.kt` | Add `transcriptUrl: String?` |
| `PodcastDatabase.kt` | Migration, version bump |
| `TranscriptSegment.kt` | New data class |
| `TranscriptRepository.kt` | New — download, cache, parse |
| `PlayerViewModel.kt` | `transcriptState`, `activeSegmentIndex`, `loadTranscript()` |
| `PlayerScreen.kt` | "Read along" button + `ReadAlongSheet` |
| `ReadAlongSheet.kt` | New composable |
| `DeepDiveModule.kt` | Provide `TranscriptRepository` |
