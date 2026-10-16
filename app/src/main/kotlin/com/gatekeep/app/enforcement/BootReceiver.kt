package com.gatekeep.app.enforcement

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                val coordinator = runCatching {
                    EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        BootReceiverEntryPoint::class.java,
                    ).coordinator()
                }.getOrNull() ?: return
                coordinator.startEnforcementService()
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootReceiverEntryPoint {
        fun coordinator(): EnforcementCoordinator
    }
}
