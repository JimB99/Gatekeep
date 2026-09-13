package com.gatekeep.app.enforcement

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.gatekeep.app.MainActivity
import com.gatekeep.app.ui.Routes

sealed class NotificationDestination {
    data object Dashboard : NotificationDestination()
    data object Stats : NotificationDestination()
    data class ProfileCurrentUsage(val profileId: Long) : NotificationDestination()

    fun toRoute(): String = when (this) {
        Dashboard -> Routes.DASHBOARD
        Stats -> Routes.STATS
        is ProfileCurrentUsage -> Routes.profileCurrentUsage(profileId)
    }
}

object NotificationDestinations {
    const val EXTRA_NAV_ROUTE = "notification_nav_route"

    fun mainActivityIntent(context: Context, destination: NotificationDestination): Intent =
        Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_NAV_ROUTE, destination.toRoute())
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

    fun mainActivityPendingIntent(
        context: Context,
        destination: NotificationDestination,
        requestCode: Int,
    ): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        mainActivityIntent(context, destination),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun readRoute(intent: Intent?): String? =
        intent?.getStringExtra(EXTRA_NAV_ROUTE)?.takeIf { it.isNotBlank() }

    fun clearRoute(intent: Intent?) {
        intent?.removeExtra(EXTRA_NAV_ROUTE)
    }
}
