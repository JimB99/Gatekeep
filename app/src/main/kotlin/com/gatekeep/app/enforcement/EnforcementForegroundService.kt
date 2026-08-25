package com.gatekeep.app.enforcement

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
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
            } else {
                stopServiceIfAccessibilityActive()
            }
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopServiceIfAccessibilityActive()
                return START_NOT_STICKY
            }
        }
        if (ForegroundMonitorAccessibilityService.instance != null) {
            stopServiceIfAccessibilityActive()
            return START_NOT_STICKY
        }
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

    private fun stopServiceIfAccessibilityActive() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_STOP = "com.gatekeep.app.action.STOP_FGS"
    }
}
