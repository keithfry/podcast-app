# Backlog: Voice Q&A on Linked Articles

**Date added:** 2026-06-23  
**Status:** Backlog

## User Story

While listening to an episode, the user can ask a voice question about a chapter's linked article — e.g. *"tell me more about the features of Gemma 4"* — and receive an in-thread spoken and/or text response synthesised from the article's content using a local LLM.

## How It Fits

Extends the existing "More About This" Deep Dive pipeline (fetch → summarize → TTS). Instead of a fixed summary, the user's question becomes the prompt context passed to the LLM alongside the article body.

## Behaviour

1. User speaks a question (via existing Tier-2 in-app mic button) while a chapter with a `url` is active.
2. System matches the utterance against a Q&A intent (e.g. contains "more about", "what is", "explain", "tell me about", question words, etc.).
3. Article body is fetched from chapter URL (reuse `UrlContentFetcher` — cache if already fetched).
4. Question + article body passed to local LLM (`GemmaTextSummarizer` or a new `ArticleQaEngine`).
5. Response text is TTS-synthesised and inserted into the playback queue (same Deep Dive injection path in `PlayerViewModel`).
6. Response text also appears as a chat-bubble overlay in the Player UI — a scrollable "in-thread" panel above the chapter list.

## Design Notes

### Intent Detection

Add `VoiceCommand.ARTICLE_QUESTION` to `VoiceCommand.kt`. Phrase matching should be broad — capture the full utterance, not just a keyword:

```kotlin
fun extractArticleQuestion(text: String): String? {
    val triggers = listOf("tell me more about", "what is", "what are", "explain", "more about", "who is")
    return if (triggers.any { text.lowercase().startsWith(it) } || text.endsWith("?")) text else null
}
```

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

- [ ] Add `VoiceCommand.ARTICLE_QUESTION` + question extraction in `VoiceCommandHandler`
- [ ] Add `ArticleQaEngine` interface + `GemmaArticleQaEngine` implementation (wraps existing `GemmaTextSummarizer` with Q&A prompt template)
- [ ] `PlayerViewModel.askArticleQuestion(question)` — fetch/cache article, call engine, TTS response, emit to Q&A flow
- [ ] `ArticleQaEntity` + `ArticleQaDao` + DB migration
- [ ] `PlayerScreen` in-thread Q&A panel composable (collapsible, below chapter list)
- [ ] Hilt binding for `ArticleQaEngine`
- [ ] Update `database.md`

## Open Questions

- Should Q&A history survive app restart? (Yes, via Room — same pattern as DeepDive)
- Max article length to pass to LLM? (Gemma context window limit — truncate or chunk)
- Should the mic button remain live during TTS playback for follow-up questions? (Desirable, but requires push-to-talk or VAD gating)
