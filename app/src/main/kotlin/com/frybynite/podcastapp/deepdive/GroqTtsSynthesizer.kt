package com.frybynite.podcastapp.deepdive

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class GroqTtsSynthesizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    @Named("groq_api_key") private val apiKey: String
) : TtsSynthesizer {

    override suspend fun synthesizeToFile(text: String): File = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", "canopylabs/orpheus-v1-english")
            put("input", text)
            put("voice", "autumn")
            put("response_format", "wav")
        }.toString()

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/speech")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        Log.i("DeepDive", "GroqTTS: POST request (text ${text.length} chars)")
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string()?.take(200) ?: ""
                val msg = "Groq TTS error ${response.code}: ${response.message} — $errBody"
                Log.e("DeepDive", msg)
                error(msg)
            }
            val file = File(context.cacheDir, "groq_tts_${System.currentTimeMillis()}.wav")
            response.body!!.byteStream().use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            Log.i("DeepDive", "GroqTTS: done — ${file.length()} bytes")
            file
        } catch (e: Exception) {
            Log.e("DeepDive", "GroqTTS: exception ${e::class.simpleName}: ${e.message}", e)
            throw e
        }
    }

    override fun release() {}
}
