package com.frybynite.podlore.deepdive

import android.util.Log
import java.io.File

// Chain: Kokoro (Modal remote) → Android TTS (on-device fallback)
class FallbackTtsSynthesizer(
    private val kokoro: KokoroTtsSynthesizer,
    private val android: AndroidTtsSynthesizer
) : TtsSynthesizer {

    override suspend fun synthesizeToFile(text: String): File {
        runCatching { return kokoro.synthesizeToFile(text) }
            .onFailure { Log.w("DeepDive", "TTS: Kokoro FAILED, falling back to Android TTS", it) }
        return android.synthesizeToFile(text)
    }

    override fun release() {
        kokoro.release()
        android.release()
    }
}
