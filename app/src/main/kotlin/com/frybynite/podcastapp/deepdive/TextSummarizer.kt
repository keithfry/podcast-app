package com.frybynite.podcastapp.deepdive

interface TextSummarizer {
    fun isModelAvailable(): Boolean
    suspend fun summarize(text: String): String
}
