package com.gatekeep.app.enforcement

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.gatekeep.app.MainActivity
import com.gatekeep.app.R
import com.gatekeep.app.util.formatDurationMs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GatekeepNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ENFORCEMENT, context.getString(R.string.channel_enforcement), NotificationManager.IMPORTANCE_LOW),
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_WARNINGS, context.getString(R.string.channel_warnings), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    fun buildEnforcementNotification(): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL_ENFORCEMENT)
            .setContentTitle(context.getString(R.string.enforcement_notification_title))
            .setContentText(context.getString(R.string.enforcement_notification_text))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    fun updateCountdown(appLabel: String, remainingDailyMs: Long?, remainingSessionMs: Long?) {
        val text = buildString {
            append(appLabel)
            append(" — ")
            append("Daily: ${formatDurationMs(remainingDailyMs)}")
            if (remainingSessionMs != null) {
                append(" | Session: ${formatDurationMs(remainingSessionMs)}")
            }
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ENFORCEMENT)
            .setContentTitle(context.getString(R.string.enforcement_notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun showWarning(title: String, text: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_WARNINGS)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .build()
        notificationManager.notify(WARNING_ID, notification)
    }

    companion object {
        const val CHANNEL_ENFORCEMENT = "enforcement"
        const val CHANNEL_WARNINGS = "warnings"
        const val NOTIFICATION_ID = 1001
        const val WARNING_ID = 1002
    }
}
