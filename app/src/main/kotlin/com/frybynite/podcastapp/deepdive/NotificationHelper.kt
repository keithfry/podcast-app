package com.frybynite.podcastapp.deepdive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.frybynite.podcastapp.MainActivity
import com.frybynite.podcastapp.R

object NotificationHelper {
    const val EXTRA_DEEP_DIVE_URL = "deep_dive_url"
    private const val CHANNEL_ID = "deep_dive_downloads"
    private const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Deep Dive Downloads",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Notifies when the AI model is ready for deep dives" }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun postReady(context: Context, pendingUrl: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DEEP_DIVE_URL, pendingUrl)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_podcast_placeholder)
            .setContentTitle("AI Model Ready")
            .setContentText("Tap to start your deep dive.")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }
}
