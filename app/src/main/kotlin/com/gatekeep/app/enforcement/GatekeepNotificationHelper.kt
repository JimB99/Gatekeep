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
import com.gatekeep.app.util.withAppLocale
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
    val hourlyUsedMs: Long? = null,
    val weeklyRemainingMs: Long? = null,
    val weeklyLimitMs: Long? = null,
    val weeklyUsedMs: Long? = null,
)

enum class UsageHudBucket {
    daily,
    hourly,
    weekly,
}

sealed class UsageHudLine {
    data class Session(val remainingMs: Long) : UsageHudLine()
    data class UsedOverLimit(
        val bucket: UsageHudBucket,
        val usedMs: Long,
        val limitMs: Long?,
    ) : UsageHudLine()
}

fun UsageHudInfo.countdownLines(): List<UsageHudLine> = buildList {
    sessionRemainingMs?.takeIf { it > 0 }?.let { add(UsageHudLine.Session(it)) }
    dailyUsedMs?.let { add(UsageHudLine.UsedOverLimit(UsageHudBucket.daily, it, dailyLimitMs)) }
    hourlyUsedMs?.let { add(UsageHudLine.UsedOverLimit(UsageHudBucket.hourly, it, hourlyLimitMs)) }
    weeklyUsedMs?.let { add(UsageHudLine.UsedOverLimit(UsageHudBucket.weekly, it, weeklyLimitMs)) }
}

fun tickHudUsedMs(
    currentUsedMs: Long?,
    remainingMs: Long?,
    limitMs: Long?,
    elapsedMs: Long,
): Long? {
    if (remainingMs != null && limitMs != null) {
        return (limitMs - remainingMs).coerceAtLeast(0)
    }
    if (currentUsedMs == null) return null
    val next = currentUsedMs + elapsedMs.coerceAtLeast(0)
    return limitMs?.let { next.coerceAtMost(it) } ?: next
}

@Singleton
class GatekeepNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val localizedContext = context.withAppLocale()
    private val notificationManager =
        localizedContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SERVICE,
                    localizedContext.getString(R.string.channel_service),
                    NotificationManager.IMPORTANCE_MIN,
                ).apply { setShowBadge(false) },
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SESSION_TIMER,
                    localizedContext.getString(R.string.channel_session_timer),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_WARNINGS,
                    localizedContext.getString(R.string.channel_warnings),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }

    fun buildServiceNotification(): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(localizedContext, CHANNEL_SERVICE)
            .setContentTitle(localizedContext.getString(R.string.enforcement_notification_title))
            .setContentText(localizedContext.getString(R.string.enforcement_notification_text))
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
    ): Boolean {
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE)
        val parts = hud.countdownLines().map { line ->
            when (line) {
                is UsageHudLine.Session -> localizedContext.getString(
                    R.string.hud_session_format,
                    formatDurationMs(context, line.remainingMs),
                )
                is UsageHudLine.UsedOverLimit -> {
                    val used = formatDurationMs(context, line.usedMs)
                    val limit = formatDurationMs(context, line.limitMs)
                    when (line.bucket) {
                        UsageHudBucket.daily -> localizedContext.getString(R.string.hud_daily_used_format, used, limit)
                        UsageHudBucket.hourly -> localizedContext.getString(R.string.hud_hourly_format, used, limit)
                        UsageHudBucket.weekly -> localizedContext.getString(R.string.hud_weekly_format, used, limit)
                    }
                }
            }
        }
        if (parts.isEmpty()) {
            hideCountdown()
            return false
        }

        val body = parts.joinToString(" · ")
        if (body == lastBody) return true

        val builder = NotificationCompat.Builder(localizedContext, CHANNEL_SESSION_TIMER)
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
        return true
    }

    fun hideCountdown() {
        notificationManager.cancel(COUNTDOWN_NOTIFICATION_ID)
    }

    fun showWarning(title: String, text: String) {
        val builder = NotificationCompat.Builder(localizedContext, CHANNEL_WARNINGS)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOnlyAlertOnce(true)
        notificationManager.notify(WARNING_ID, builder.build())
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
