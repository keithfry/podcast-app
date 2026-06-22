package com.frybynite.podlore.deepdive

import android.util.Log

// AiCoreTextSummarizer is excluded from this pipeline (BINDING_FAILURE on Pixel 7).
// Chain: LiteRt (on-device Qwen3-0.6B) → Gemma (on-device MediaPipe CPU) → Groq (remote)
class FallbackTextSummarizer(
    private val litert: LiteRtTextSummarizer,
    private val gemma: GemmaTextSummarizer,
    private val groq: GroqTextSummarizer
) : TextSummarizer {

    override fun isModelAvailable(): Boolean =
        litert.isModelAvailable() || gemma.isModelAvailable()

    override suspend fun summarize(text: String, existingSummary: String?): String {
        // 1. LiteRt — Qwen3-0.6B on-device
        if (litert.isModelAvailable()) {
            Log.i("DeepDive", "Summarizer: attempting on-device LiteRt (Qwen3-0.6B), text=${text.length} chars")
            runCatching { return litert.summarize(text, existingSummary) }
                .onFailure { Log.w("DeepDive", "Summarizer: LiteRt FAILED, trying Gemma", it) }
        }

        // 2. Gemma — MediaPipe CPU on-device
        if (gemma.isModelAvailable()) {
            Log.i("DeepDive", "Summarizer: attempting on-device Gemma (MediaPipe CPU), text=${text.length} chars")
            runCatching {
                val result = gemma.summarize(text, existingSummary)
                Log.i("DeepDive", "Summarizer: Gemma on-device SUCCESS, output=${result.length} chars")
                return result
            }.onFailure { Log.w("DeepDive", "Summarizer: Gemma FAILED, falling back to Groq (remote)", it) }
        } else {
            Log.i("DeepDive", "Summarizer: no on-device model available, using Groq (remote)")
        }

        // 3. Groq — remote cloud fallback
        Log.i("DeepDive", "Summarizer: using Groq (remote), text=${text.length} chars")
        val result = groq.summarize(text, existingSummary)
        Log.i("DeepDive", "Summarizer: remote (Groq) SUCCESS, output=${result.length} chars")
        return result
    }
}
