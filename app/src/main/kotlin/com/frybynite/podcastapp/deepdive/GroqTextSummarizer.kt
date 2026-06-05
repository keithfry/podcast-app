package com.frybynite.podcastapp.deepdive

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class GroqTextSummarizer @Inject constructor(
    private val client: OkHttpClient,
    @Named("groq_api_key") private val apiKey: String
) : TextSummarizer {

    @JsonClass(generateAdapter = false)
    private data class GroqRequestMessage(val role: String, val content: String)

    @JsonClass(generateAdapter = false)
    private data class GroqRequest(
        val model: String,
        val messages: List<GroqRequestMessage>,
        @Json(name = "max_tokens") val maxTokens: Int
    )

    @JsonClass(generateAdapter = false)
    private data class GroqResponseMessage(val role: String? = null, val content: String)

    @JsonClass(generateAdapter = false)
    private data class GroqChoice(val message: GroqResponseMessage)

    @JsonClass(generateAdapter = false)
    private data class GroqResponse(val choices: List<GroqChoice>)

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val requestAdapter = moshi.adapter(GroqRequest::class.java)
    private val responseAdapter = moshi.adapter(GroqResponse::class.java)

    override fun isModelAvailable(): Boolean = true

    override suspend fun summarize(text: String, existingSummary: String?): String = withContext(Dispatchers.IO) {
        val (sentenceTarget, maxTokens) = when {
            text.length < 1_000  -> "2 to 3 sentences" to 256
            text.length < 3_000  -> "4 to 5 sentences" to 400
            text.length < 6_000  -> "6 to 8 sentences" to 600
            else                 -> "as many sentences as needed to cover all key points and major sections, up to about a paragraph per major theme" to 900
        }

        val knownLine = if (existingSummary != null)
            "\nWhat they already know: $existingSummary\n" else ""
        val depthLine = if (existingSummary != null)
            "Go beyond what they already know. " else ""

        val prompt = """You are helping a podcast listener go deeper on a story they just heard.
$knownLine
Full article: $text

${depthLine}Focus on technical details, how it works, implications, and anything surprising or non-obvious. Write in clear, natural spoken prose — this will be read aloud by a text-to-speech voice, so avoid bullet points, headers, markdown, abbreviations, and symbols. Aim for $sentenceTarget."""

        val requestBody = requestAdapter.toJson(
            GroqRequest(
                model = "llama-3.1-8b-instant",
                messages = listOf(GroqRequestMessage(role = "user", content = prompt)),
                maxTokens = maxTokens
            )
        )

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        Log.i("DeepDive", "Groq: POST request (text ${text.length} chars, existingSummary=${existingSummary != null})")
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            val msg = "Groq API error ${response.code}: $errorBody"
            Log.e("DeepDive", msg)
            throw IllegalStateException(msg)
        }

        val responseJson = responseAdapter.fromJson(response.body!!.string())
            ?: throw IllegalStateException("Failed to parse Groq API response")

        val result = responseJson.choices.first().message.content.trim()
        Log.i("DeepDive", "Groq: response ${result.length} chars")
        result
    }
}
