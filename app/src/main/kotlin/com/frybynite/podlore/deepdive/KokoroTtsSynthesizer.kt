package com.frybynite.podlore.deepdive

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
class KokoroTtsSynthesizer @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("kokoro_client") private val client: OkHttpClient,
    @Named("modal_tts_url") private val endpointUrl: String
) : TtsSynthesizer {

    override suspend fun synthesizeToFile(text: String): File = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("text", text)
            put("voice", "af_sky")
        }.toString()

        val request = Request.Builder()
            .url(endpointUrl)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        Log.i("DeepDive", "Kokoro: POST to Modal endpoint (text ${text.length} chars)")
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string()?.take(200) ?: ""
                val msg = "Kokoro error ${response.code}: ${response.message} — $errBody"
                Log.e("DeepDive", msg)
                error(msg)
            }
            val file = File(context.cacheDir, "kokoro_${System.currentTimeMillis()}.wav")
            response.body!!.byteStream().use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            Log.i("DeepDive", "Kokoro: done — ${file.length()} bytes written")
            file
        } catch (e: Exception) {
            Log.e("DeepDive", "Kokoro: exception ${e::class.simpleName}: ${e.message}", e)
            throw e
        }
    }

    override fun release() {}
}
