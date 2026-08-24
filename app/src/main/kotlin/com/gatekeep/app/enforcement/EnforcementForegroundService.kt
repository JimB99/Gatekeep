package com.gatekeep.app.enforcement

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class EnforcementForegroundService : Service() {

    @Inject lateinit var notificationHelper: GatekeepNotificationHelper
    @Inject lateinit var coordinator: EnforcementCoordinator

    private val handler = Handler(Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            if (ForegroundMonitorAccessibilityService.instance == null) {
                coordinator.pollFallbackForeground()
            }
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            GatekeepNotificationHelper.SERVICE_NOTIFICATION_ID,
            notificationHelper.buildServiceNotification(),
        )
        handler.removeCallbacks(pollRunnable)
        handler.postDelayed(pollRunnable, 30_000L)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        super.onDestroy()
    }
}
