# Cloud Deep Dive Design

## Goal

Replace slow/crashy on-device CPU inference with cloud-based summarization (Groq) and TTS (Kokoro via HuggingFace), while keeping GPU on-device as the preferred path.

## Priority Order

1. **GPU on-device** — OpenCL available → `GemmaTextSummarizer` + `AndroidTtsSynthesizer`
2. **Cloud** — OpenCL not available → `GroqTextSummarizer` + `KokoroTtsSynthesizer`
3. **CPU on-device** — code remains, not auto-selected (manual/debug only)

## Architecture

`TextSummarizer` and `TtsSynthesizer` are already interfaces. Add cloud implementations and a Hilt module that selects the right binding at runtime based on `isOpenClSupported()`.

## Components

### `OpenClDetector` (new utility)
Extract `isOpenClSupported()` from `ModelDownloadManager` into a shared singleton so both download and Hilt module can use it without duplication.

```kotlin
object OpenClDetector {
    fun isSupported(): Boolean = try {
        System.loadLibrary("OpenCL"); true
    } catch (e: UnsatisfiedLinkError) { false }
}
```

### `GroqTextSummarizer` (new)
- File: `app/src/main/kotlin/com/frybynite/podcastapp/deepdive/GroqTextSummarizer.kt`
- POST to `https://api.groq.com/openai/v1/chat/completions`
- Model: `llama-3.1-8b-instant`
- Same prompt logic as `GemmaTextSummarizer` (with `existingSummary` context)
- API key: `BuildConfig.GROQ_API_KEY` (from `local.properties`)

### `KokoroTtsSynthesizer` (new)
- File: `app/src/main/kotlin/com/frybynite/podcastapp/deepdive/KokoroTtsSynthesizer.kt`
- POST to `https://api-inference.huggingface.co/models/hexgrad/Kokoro-82M`
- Header: `Authorization: Bearer <HF_TOKEN>`
- Body: `{"inputs": "<text>", "parameters": {"voice": "af_sky"}}`
- Response: audio bytes → write to temp file in `context.cacheDir`
- HF token: `BuildConfig.HF_TOKEN` (from `local.properties`)

### `DeepDiveModule` (update)
Replace the current static `@Binds` with `@Provides` that checks `OpenClDetector.isSupported()`:

```kotlin
@Provides @Singleton
fun provideTextSummarizer(
    gemma: GemmaTextSummarizer,
    groq: GroqTextSummarizer
): TextSummarizer = if (OpenClDetector.isSupported()) gemma else groq

@Provides @Singleton
fun provideTtsSynthesizer(
    android: TtsSynthesizer,
    kokoro: KokoroTtsSynthesizer
): TtsSynthesizer = if (OpenClDetector.isSupported()) android else kokoro
```

Note: `TtsSynthesizer` needs to become an interface (currently a concrete class). `AndroidTtsSynthesizer` becomes the Android implementation.

### `ModelDownloadManager` (update)
Use `OpenClDetector.isSupported()` instead of inline `isOpenClSupported()`. Skip download entirely on cloud path.

## API Keys

Add to `local.properties` (not committed):
```
GROQ_API_KEY=your_key_here
HF_TOKEN=your_token_here
```

Expose via `app/build.gradle.kts`:
```kotlin
buildConfigField("String", "GROQ_API_KEY", "\"${localProperties["GROQ_API_KEY"]}\"")
buildConfigField("String", "HF_TOKEN", "\"${localProperties["HF_TOKEN"]}\"")
```

Add `local.properties` to `.gitignore` if not already present.

## Error Handling

- Groq API error → surface via `DeepDiveState.Error` (same as on-device path)
- Kokoro API error → same
- HuggingFace model loading (cold start ~20s) → retry once before failing

## Testing

- Unit test `GroqTextSummarizer` with mockk OkHttpClient
- Unit test `KokoroTtsSynthesizer` with mockk OkHttpClient
- `assembleDebug` must pass with placeholder keys in CI

## Getting API Keys

- **Groq:** console.groq.com → free, no credit card
- **HuggingFace:** huggingface.co → Settings → Access Tokens → New token (read)
