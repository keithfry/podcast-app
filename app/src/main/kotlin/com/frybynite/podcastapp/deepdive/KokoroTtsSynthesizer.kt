package com.frybynite.podcastapp.deepdive

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
class KokoroTtsSynthesizer @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("kokoro_client") private val client: OkHttpClient,
    @Named("hf_token") private val hfToken: String
) : TtsSynthesizer {

    override suspend fun synthesizeToFile(text: String): File = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("inputs", text)
            put("parameters", JSONObject().apply {
                put("voice", "af_sky")
            })
        }.toString()

        val request = Request.Builder()
            .url("https://api-inference.huggingface.co/models/hexgrad/Kokoro-82M")
            .addHeader("Authorization", "Bearer $hfToken")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        Log.i("DeepDive", "Kokoro: POST request (text ${text.length} chars)")
        try {
            var response = client.newCall(request).execute()
            if (response.code == 503) {
                Log.i("DeepDive", "Kokoro: 503 cold start — retrying in 25s")
                response.close()
                delay(25_000)
                response = client.newCall(request).execute()
            }
            if (!response.isSuccessful) {
                val errBody = response.body?.string()?.take(200) ?: ""
                val msg = "Kokoro API error ${response.code}: ${response.message} — $errBody"
                Log.e("DeepDive", msg)
                error(msg)
            }
            Log.i("DeepDive", "Kokoro: response OK (${response.body?.contentLength()} bytes)")

            val file = File(context.cacheDir, "kokoro_${System.currentTimeMillis()}.wav")
            response.body!!.byteStream().use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file
        } catch (e: Exception) {
            Log.e("DeepDive", "Kokoro: exception ${e::class.simpleName}: ${e.message}", e)
            throw e
        }
    }

    override fun release() {
        // no-op, stateless
    }
}
