package com.gatekeep.app.enforcement

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class EnforcementForegroundService : Service() {

    @Inject lateinit var notificationHelper: GatekeepNotificationHelper

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            GatekeepNotificationHelper.NOTIFICATION_ID,
            notificationHelper.buildEnforcementNotification(),
        )
        return START_STICKY
    }
}
