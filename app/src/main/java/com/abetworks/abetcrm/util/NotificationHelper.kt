package com.abetworks.abetcrm.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationHelper {
    const val CHANNEL_LEAD_CAPTURE = "lead_capture"
    const val CHANNEL_FOLLOW_UP    = "follow_up"
    const val CHANNEL_SYNC         = "sync"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_LEAD_CAPTURE, "New Lead Captured",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Alerts when a new lead is auto-captured from a call or WhatsApp" })

        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_FOLLOW_UP, "Follow-up Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Reminders for scheduled lead follow-ups" })

        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_SYNC, "Background Sync",
            NotificationManager.IMPORTANCE_MIN
        ).apply { description = "Cloud sync status" })
    }
}
