package com.frybynite.podlore.data.preferences

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpisodeListPreferences @Inject constructor(private val prefs: SharedPreferences) {
    fun getShowHeard(feedUrl: String): Boolean = prefs.getBoolean("showHeard_$feedUrl", false)
    fun setShowHeard(feedUrl: String, show: Boolean) =
        prefs.edit().putBoolean("showHeard_$feedUrl", show).apply()
}
