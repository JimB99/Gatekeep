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

data class UsageHudInfo(
    val sessionRemainingMs: Long? = null,
    val dailyRemainingMs: Long? = null,
    val dailyLimitMs: Long? = null,
    val dailyUsedMs: Long? = null,
    val hourlyRemainingMs: Long? = null,
    val hourlyLimitMs: Long? = null,
    val weeklyRemainingMs: Long? = null,
    val weeklyLimitMs: Long? = null,
)

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
                NotificationChannel(
                    CHANNEL_SERVICE,
                    context.getString(R.string.channel_service),
                    NotificationManager.IMPORTANCE_MIN,
                ).apply { setShowBadge(false) },
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SESSION_TIMER,
                    context.getString(R.string.channel_session_timer),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_WARNINGS,
                    context.getString(R.string.channel_warnings),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }

    fun buildServiceNotification(): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setContentTitle(context.getString(R.string.enforcement_notification_title))
            .setContentText(context.getString(R.string.enforcement_notification_text))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    fun showCountdown(
        title: String,
        hud: UsageHudInfo,
        lastBody: String? = null,
        onBodyPosted: (String) -> Unit = {},
    ) {
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE)
        val parts = buildList {
            hud.sessionRemainingMs?.let {
                add(context.getString(R.string.hud_session_format, formatDurationMs(context, it)))
            }
            if (hud.dailyLimitMs != null && hud.dailyUsedMs != null) {
                add(
                    context.getString(
                        R.string.hud_daily_used_format,
                        formatDurationMs(context, hud.dailyUsedMs),
                        formatDurationMs(context, hud.dailyLimitMs),
                    ),
                )
            } else {
                hud.dailyRemainingMs?.let {
                    add(context.getString(R.string.hud_daily_left_format, formatDurationMs(context, it)))
                }
            }
            if (hud.hourlyLimitMs != null && hud.hourlyRemainingMs != null) {
                val used = (hud.hourlyLimitMs - hud.hourlyRemainingMs).coerceAtLeast(0)
                add(
                    context.getString(
                        R.string.hud_hourly_format,
                        formatDurationMs(context, used),
                        formatDurationMs(context, hud.hourlyLimitMs),
                    ),
                )
            }
            if (hud.weeklyLimitMs != null && hud.weeklyRemainingMs != null) {
                val used = (hud.weeklyLimitMs - hud.weeklyRemainingMs).coerceAtLeast(0)
                add(
                    context.getString(
                        R.string.hud_weekly_format,
                        formatDurationMs(context, used),
                        formatDurationMs(context, hud.weeklyLimitMs),
                    ),
                )
            }
        }
        if (parts.isEmpty()) return

        val body = parts.joinToString(" · ")
        if (body == lastBody) return

        val builder = NotificationCompat.Builder(context, CHANNEL_SESSION_TIMER)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(parts.joinToString("\n")))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
        notificationManager.notify(COUNTDOWN_NOTIFICATION_ID, builder.build())
        onBodyPosted(body)
    }

    fun hideCountdown() {
        notificationManager.cancel(COUNTDOWN_NOTIFICATION_ID)
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
        const val CHANNEL_SERVICE = "gatekeep_service"
        const val CHANNEL_SESSION_TIMER = "session_timer"
        const val CHANNEL_WARNINGS = "warnings"
        const val SERVICE_NOTIFICATION_ID = 1001
        const val COUNTDOWN_NOTIFICATION_ID = 1003
        const val WARNING_ID = 1002

        @Deprecated("Use SERVICE_NOTIFICATION_ID", ReplaceWith("SERVICE_NOTIFICATION_ID"))
        const val NOTIFICATION_ID = SERVICE_NOTIFICATION_ID
    }
}
