# Backlog: Voice Q&A on Linked Articles

**Date added:** 2026-06-23  
**Status:** Backlog

## User Story

While listening to an episode — on phone or via Android Auto — the user can ask a voice question about a chapter's linked article, e.g. *"tell me more about the features of Gemma 4"*, and receive a spoken response synthesised from the article's content using a local LLM. On phone, the response also appears as an in-thread text panel.

## How It Fits

Extends the existing "More About This" Deep Dive pipeline (fetch → summarize → TTS). Instead of a fixed summary, the user's question becomes the prompt context passed to the LLM alongside the article body.

## Behaviour

### Phone (Tier-2 mic button)

1. User taps mic button and speaks a question while a chapter with a `url` is active.
2. System matches utterance against Q&A intent (contains "more about", "what is", "explain", question words, etc.).
3. Article body fetched from chapter URL (reuse `UrlContentFetcher` — cache if already fetched).
4. Question + article body passed to local LLM (`ArticleQaEngine`).
5. Response TTS-synthesised and inserted into playback queue (same Deep Dive injection path in `PlayerViewModel`).
6. Response also appears as chat-bubble overlay in Player UI — scrollable in-thread panel above chapter list.

### Android Auto (Tier-1 voice via Google Assistant / MediaSession)

1. User says **"Hey Google, ask Podlore [question]"** or activates the Auto voice button while Podlore is the active media app.
2. Auto routes the utterance to Podlore via a `SessionCommand` (`ARTICLE_QUESTION`) sent from a registered `MediaSession.Callback`.
3. Same fetch → LLM → TTS pipeline runs on-device.
4. TTS audio injected into the playback queue — response plays through car speakers automatically.
5. No visual panel in Auto (driver safety); optionally show a brief notification card via `CarAppExtender` if the platform supports it.

## Design Notes

### Intent Detection (Phone)

Add `VoiceCommand.ARTICLE_QUESTION` to `VoiceCommand.kt`. Phrase matching should be broad — capture the full utterance, not just a keyword:

```kotlin
fun extractArticleQuestion(text: String): String? {
    val triggers = listOf("tell me more about", "what is", "what are", "explain", "more about", "who is")
    return if (triggers.any { text.lowercase().startsWith(it) } || text.endsWith("?")) text else null
}
```

### Android Auto Integration

Auto voice commands arrive as `CustomAction` or `SessionCommand` callbacks in `PlaybackService` (which is already a `MediaLibraryService`). Steps:

1. **Register custom command** — declare `ARTICLE_QUESTION` as a `SessionCommand` in `PlaybackService.onGetSession()`:
   ```kotlin
   val articleQaCommand = SessionCommand("ARTICLE_QUESTION", Bundle.EMPTY)
   ```
   Add it to `MediaSession.Builder.setSessionActivity(...)` and the `ConnectionResult.AcceptedResultBuilder` so Auto knows the command exists.

2. **Handle in `MediaSession.Callback.onCustomCommand`** — extract the question string from `args`, dispatch to the same `PlayerViewModel.askArticleQuestion(question)` path (or an equivalent service-level coroutine if the VM isn't reachable from the service).

3. **Google Assistant hook** — declare an `android.media.action.MEDIA_PLAY_FROM_SEARCH` intent filter and handle the `EXTRA_MEDIA_FOCUS` / query extras in `PlaybackService.onPlayFromSearch`. Map free-text queries that look like questions to `ARTICLE_QUESTION` before passing to MediaSession.

4. **TTS playback in Auto** — no UI change needed; the existing `PlayerViewModel` injects TTS audio into the ExoPlayer queue, which Auto renders automatically through the car's audio system.

5. **Safety constraint** — do not start LLM inference while the vehicle is moving if it would delay audio by >2s. Gate on: queue the request immediately, play a brief "thinking…" TTS clip, then insert the answer when ready. This prevents a silent gap that could confuse the driver.

### LLM Prompt

```
Article: <article body>

Question: <user question>

Answer concisely in 3–5 sentences based only on the article above.
```

If article body is already cached in `CacheStorage` (metadata.json), skip re-fetch.

### In-Thread Panel

- Collapsible overlay in `PlayerScreen`, similar to the existing chapter list panel.
- Each Q&A exchange is a row: user question (right-aligned) + LLM answer (left-aligned).
- Persisted in a new `ArticleQaEntity` Room table keyed on `(episodeAudioUrl, chapterUrl)`.
- State managed in `PlayerViewModel` via a `Flow<List<QaExchange>>`.

### New DB Table

```kotlin
@Entity(tableName = "article_qa")
data class ArticleQaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val episodeAudioUrl: String,
    val chapterUrl: String,
    val question: String,
    val answer: String,
    val createdAt: Long,
)
```

Bump DB version and add migration.

## Implementation Tasks

### Shared pipeline
- [ ] Add `ArticleQaEngine` interface + `GemmaArticleQaEngine` impl (Q&A prompt template wrapping `GemmaTextSummarizer`)
- [ ] `PlayerViewModel.askArticleQuestion(question)` — fetch/cache article, call engine, TTS-synthesise, inject into queue, emit to Q&A state flow
- [ ] `ArticleQaEntity` + `ArticleQaDao` + DB migration
- [ ] Hilt binding for `ArticleQaEngine`
- [ ] Update `database.md`

### Phone (mic button)
- [ ] Add `VoiceCommand.ARTICLE_QUESTION` + `extractArticleQuestion()` in `VoiceCommandHandler`
- [ ] `PlayerScreen` in-thread Q&A panel composable (collapsible, below chapter list)

### Android Auto
- [ ] Register `ARTICLE_QUESTION` `SessionCommand` in `PlaybackService.onGetSession()`
- [ ] Handle in `MediaSession.Callback.onCustomCommand` → dispatch to `askArticleQuestion`
- [ ] Handle `onPlayFromSearch` in `PlaybackService` — map question-like queries to `ARTICLE_QUESTION`
- [ ] "Thinking…" TTS bridge clip to avoid silent gap while LLM runs
- [ ] Declare `MEDIA_PLAY_FROM_SEARCH` intent filter in `AndroidManifest.xml`

## Open Questions

- Max article length to pass to LLM? (Gemma context window limit — truncate to ~2 000 tokens or chunk + retrieve)
- Should the mic button stay live during TTS playback for follow-up questions? (Desirable — requires push-to-talk or VAD gating to avoid mic picking up speaker output)
- In Auto: fire-and-forget (queue answer, play when ready) or block playback until answer arrives? (Fire-and-forget preferred — less disruptive)
