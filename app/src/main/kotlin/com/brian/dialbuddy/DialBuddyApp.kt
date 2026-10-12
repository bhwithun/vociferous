package com.brian.dialbuddy

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class DialBuddyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CALL_CHANNEL_ID,
            getString(R.string.call_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.call_channel_description)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CALL_CHANNEL_ID = "calls"
    }
}
