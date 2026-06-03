package com.podcastapp.data.preferences

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeedPreferences @Inject constructor(private val prefs: SharedPreferences) {
    companion object {
        private const val KEY_SPEED = "playback_speed"
        const val DEFAULT_SPEED = 1.0f
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 2.0f
    }

    var speed: Float
        get() = prefs.getFloat(KEY_SPEED, DEFAULT_SPEED)
        set(value) = prefs.edit().putFloat(KEY_SPEED, value).apply()
}
