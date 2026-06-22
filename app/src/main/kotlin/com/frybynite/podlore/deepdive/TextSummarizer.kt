package com.frybynite.podlore.deepdive

interface TextSummarizer {
    fun isModelAvailable(): Boolean
    suspend fun summarize(text: String, existingSummary: String? = null): String
}
