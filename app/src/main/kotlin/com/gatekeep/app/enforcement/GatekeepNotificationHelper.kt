package com.gatekeep.app.enforcement

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.gatekeep.app.R
import com.gatekeep.domain.EnforcementPollInterval
import com.gatekeep.app.util.PermissionHelper
import com.gatekeep.app.util.formatDurationMinutes
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

data class SessionHudDisplay(
    val displayMs: Long,
    val includeSeconds: Boolean,
)

fun sessionHudDisplay(remainingMs: Long, pollIntervalMs: Long): SessionHudDisplay {
    val displayMs = EnforcementPollInterval.sessionDisplayRemainingMs(remainingMs, pollIntervalMs)
    val includeSeconds = pollIntervalMs <= EnforcementPollInterval.FINE_INTERVAL_MS
    return SessionHudDisplay(displayMs, includeSeconds)
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
    if (remainingMs != null && limitMs == null) {
        return currentUsedMs
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
        val pending = NotificationDestinations.mainActivityPendingIntent(
            context,
            NotificationDestination.Dashboard,
            PENDING_SERVICE,
        )
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

    fun resetCountdownDismissState() {
        CountdownNotificationState.reset()
    }

    fun showCountdown(
        title: String,
        hud: UsageHudInfo,
        profileId: Long? = null,
        lastBody: String? = null,
        onBodyPosted: (String) -> Unit = {},
        sessionPollIntervalMs: Long = EnforcementPollInterval.FINE_INTERVAL_MS,
    ): Boolean {
        if (CountdownNotificationState.dismissedByUser) return false

        val destination = profileId?.let { NotificationDestination.ProfileCurrentUsage(it) }
            ?: NotificationDestination.Dashboard
        val pending = NotificationDestinations.mainActivityPendingIntent(
            context,
            destination,
            countdownPendingRequestCode(profileId),
        )
        val dismissIntent = PendingIntent.getBroadcast(
            context,
            COUNTDOWN_DISMISS_REQUEST_CODE,
            Intent(context, CountdownNotificationDismissReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val parts = hud.countdownLines().map { line ->
            when (line) {
                is UsageHudLine.Session -> {
                    val sessionDisplay = sessionHudDisplay(line.remainingMs, sessionPollIntervalMs)
                    val formatted = if (sessionDisplay.includeSeconds) {
                        formatDurationMs(context, sessionDisplay.displayMs)
                    } else {
                        formatDurationMinutes(sessionDisplay.displayMs)
                    }
                    localizedContext.getString(R.string.hud_session_format, formatted)
                }
                is UsageHudLine.UsedOverLimit -> {
                    val used = formatDurationMinutes(line.usedMs)
                    val limit = formatDurationMinutes(line.limitMs)
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
            .setDeleteIntent(dismissIntent)
            .setOngoing(false)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
        notificationManager.notify(COUNTDOWN_NOTIFICATION_ID, builder.build())
        onBodyPosted(body)
        return true
    }

    fun hideCountdown() {
        notificationManager.cancel(COUNTDOWN_NOTIFICATION_ID)
    }

    fun showApproachingLimitWarning(profileId: Long, appLabel: String) {
        showWarningNotification(
            title = localizedContext.getString(R.string.approaching_limit_title),
            text = localizedContext.getString(R.string.approaching_limit_body, appLabel),
            destination = NotificationDestination.ProfileCurrentUsage(profileId),
            notificationId = APPROACHING_LIMIT_ID,
            pendingRequestCode = PENDING_APPROACHING_LIMIT,
        )
    }

    fun showLimitReachedWarning(profileId: Long, title: String, text: String) {
        showWarningNotification(
            title = title,
            text = text,
            destination = NotificationDestination.ProfileCurrentUsage(profileId),
            notificationId = LIMIT_REACHED_ID,
            pendingRequestCode = PENDING_LIMIT_REACHED,
        )
    }

    fun showWeeklyReportWarning() {
        showWarningNotification(
            title = localizedContext.getString(R.string.weekly_report_title),
            text = localizedContext.getString(R.string.weekly_report_body),
            destination = NotificationDestination.Stats,
            notificationId = WEEKLY_REPORT_ID,
            pendingRequestCode = PENDING_WEEKLY_REPORT,
        )
    }

    private fun showWarningNotification(
        title: String,
        text: String,
        destination: NotificationDestination,
        notificationId: Int,
        pendingRequestCode: Int,
    ) {
        val pending = NotificationDestinations.mainActivityPendingIntent(
            context,
            destination,
            pendingRequestCode,
        )
        val builder = NotificationCompat.Builder(localizedContext, CHANNEL_WARNINGS)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
        notificationManager.notify(notificationId, builder.build())
    }

    private fun countdownPendingRequestCode(profileId: Long?): Int =
        if (profileId == null) {
            PENDING_COUNTDOWN
        } else {
            PENDING_COUNTDOWN_PROFILE_BASE + (profileId % PENDING_COUNTDOWN_PROFILE_SPAN).toInt()
        }

    fun showAccessibilityRevoked() {
        val settingsIntent = PermissionHelper.accessibilityIntent(context).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pending = PendingIntent.getActivity(
            context,
            2,
            settingsIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(localizedContext, CHANNEL_WARNINGS)
            .setContentTitle(localizedContext.getString(R.string.accessibility_revoked_notification_title))
            .setContentText(localizedContext.getString(R.string.accessibility_revoked_notification_body))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(localizedContext.getString(R.string.accessibility_revoked_notification_body)),
            )
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        notificationManager.notify(ACCESSIBILITY_REVOKED_ID, builder.build())
    }

    fun hideAccessibilityRevoked() {
        notificationManager.cancel(ACCESSIBILITY_REVOKED_ID)
    }

    companion object {
        const val CHANNEL_SERVICE = "gatekeep_service"
        const val CHANNEL_SESSION_TIMER = "session_timer"
        const val CHANNEL_WARNINGS = "warnings"
        const val SERVICE_NOTIFICATION_ID = 1001
        const val COUNTDOWN_NOTIFICATION_ID = 1003
        const val COUNTDOWN_DISMISS_REQUEST_CODE = 2003
        const val LIMIT_REACHED_ID = 1002
        const val APPROACHING_LIMIT_ID = 1005
        const val WEEKLY_REPORT_ID = 1006
        const val ACCESSIBILITY_REVOKED_ID = 1004

        private const val PENDING_SERVICE = 0
        private const val PENDING_COUNTDOWN = 1
        private const val PENDING_COUNTDOWN_PROFILE_BASE = 100
        private const val PENDING_COUNTDOWN_PROFILE_SPAN = 1_000
        private const val PENDING_APPROACHING_LIMIT = 10
        private const val PENDING_LIMIT_REACHED = 11
        private const val PENDING_WEEKLY_REPORT = 12

        @Deprecated("Use LIMIT_REACHED_ID", ReplaceWith("LIMIT_REACHED_ID"))
        const val WARNING_ID = LIMIT_REACHED_ID

        @Deprecated("Use SERVICE_NOTIFICATION_ID", ReplaceWith("SERVICE_NOTIFICATION_ID"))
        const val NOTIFICATION_ID = SERVICE_NOTIFICATION_ID
    }
}
