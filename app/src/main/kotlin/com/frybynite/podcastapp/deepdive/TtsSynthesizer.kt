package com.frybynite.podcastapp.deepdive

import java.io.File

interface TtsSynthesizer {
    suspend fun synthesizeToFile(text: String): File
    fun release()
}
