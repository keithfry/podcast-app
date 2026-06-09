package com.frybynite.podcastapp.deepdive

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiteRtTextSummarizer @Inject constructor(
    @ApplicationContext private val context: Context
) : TextSummarizer {

    companion object {
        // 474 MB INT4 quantized Qwen3-0.6B
        const val MODEL_URL = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/qwen3_0_6b_mixed_int4.litertlm"
        const val MODEL_FILE = "models/qwen3-0.6b-int4.litertlm"
        private const val MIN_VALID_SIZE = 400_000_000L
    }

    private val modelFile get() = context.filesDir.resolve(MODEL_FILE)

    @Volatile private var engine: Engine? = null

    override fun isModelAvailable(): Boolean = modelFile.exists() && modelFile.length() > MIN_VALID_SIZE

    private suspend fun ensureEngine(): Engine = withContext(Dispatchers.Default) {
        engine ?: synchronized(this@LiteRtTextSummarizer) {
            engine ?: createEngine().also { engine = it }
        }
    }

    private fun createEngine(): Engine {
        val path = modelFile.absolutePath
        runCatching {
            Log.i("DeepDive", "LiteRt: initializing with GPU backend")
            val e = Engine(EngineConfig(modelPath = path, backend = Backend.GPU()))
            e.initialize()
            Log.i("DeepDive", "LiteRt: GPU engine ready")
            return e
        }.onFailure {
            Log.w("DeepDive", "LiteRt: GPU init failed (${it.message?.take(80)}), falling back to CPU")
        }
        Log.i("DeepDive", "LiteRt: initializing with CPU backend")
        val e = Engine(EngineConfig(modelPath = path, backend = Backend.CPU()))
        e.initialize()
        Log.i("DeepDive", "LiteRt: CPU engine ready")
        return e
    }

    override suspend fun summarize(text: String, existingSummary: String?): String = withContext(Dispatchers.Default) {
        val e = ensureEngine()
        val conversation = e.createConversation()
        val prompt = buildPrompt(text, existingSummary)
        Log.i("DeepDive", "LiteRt: generating, text=${text.length} chars existingSummary=${existingSummary != null}")
        val sb = StringBuilder()
        conversation.sendMessageAsync(prompt).collect { msg ->
            msg.contents.contents.filterIsInstance<Content.Text>().forEach { sb.append(it.text) }
        }
        conversation.close()
        val raw = sb.toString()
        // Strip Qwen3 think blocks (present when /no_think is ignored or partial)
        val result = raw.replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
        if (result.isEmpty()) error("LiteRt returned empty response")
        Log.i("DeepDive", "LiteRt: on-device SUCCESS, output=${result.length} chars\n$result")
        result
    }

    private fun buildPrompt(text: String, existingSummary: String?): String {
        val sentenceTarget = when {
            text.length < 1_000 -> "2 to 3 sentences"
            text.length < 3_000 -> "4 to 5 sentences"
            text.length < 6_000 -> "6 to 8 sentences"
            else                -> "a paragraph per major theme"
        }
        val knownLine = if (existingSummary != null) "\nWhat they already know: $existingSummary\n" else ""
        val depthLine = if (existingSummary != null) "Go beyond what they already know. " else ""
        val instruction = """You are helping a podcast listener go deeper on a story.
$knownLine
Full article: $text

${depthLine}Focus on technical details, how it works, implications, and anything surprising or non-obvious. Write in clear, natural spoken prose — avoid bullet points, headers, markdown, abbreviations, and symbols. Aim for $sentenceTarget."""

        return "/no_think\n$instruction"
    }
}
