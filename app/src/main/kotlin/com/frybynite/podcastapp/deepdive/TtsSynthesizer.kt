package com.frybynite.podcastapp.deepdive

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class TtsSynthesizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null
    private var ready = false

    private suspend fun ensureReady() = suspendCancellableCoroutine<Unit> { cont ->
        if (ready) { cont.resume(Unit); return@suspendCancellableCoroutine }
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) { ready = true; cont.resume(Unit) }
            else cont.resumeWithException(IllegalStateException("TTS init failed: $status"))
        }
    }

    suspend fun synthesizeToFile(text: String): File {
        ensureReady()
        val file = File(context.cacheDir, "tts_${UUID.randomUUID()}.wav")
        suspendCancellableCoroutine<Unit> { cont ->
            val id = UUID.randomUUID().toString()
            tts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {}
                override fun onDone(utteranceId: String) { if (utteranceId == id) cont.resume(Unit) }
                @Deprecated("Deprecated in API 21")
                override fun onError(utteranceId: String) {
                    cont.resumeWithException(RuntimeException("TTS synthesis failed"))
                }
            })
            tts!!.synthesizeToFile(text, Bundle(), file, id)
        }
        return file
    }

    fun release() { tts?.stop(); tts?.shutdown(); tts = null; ready = false }
}
