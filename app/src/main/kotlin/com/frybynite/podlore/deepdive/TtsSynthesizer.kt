package com.frybynite.podlore.deepdive

import java.io.File

interface TtsSynthesizer {
    suspend fun synthesizeToFile(text: String): File
    fun release()
}
