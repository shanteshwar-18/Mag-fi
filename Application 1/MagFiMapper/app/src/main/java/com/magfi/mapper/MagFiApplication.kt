package com.magfi.mapper

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class MagFiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MappingForegroundService.CHANNEL_ID,
                "Mag-Fi Mapping",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mag-Fi fingerprint recording notification"
            }
            val notifManager = getSystemService(NotificationManager::class.java)
            notifManager.createNotificationChannel(channel)
        }
    }
}
