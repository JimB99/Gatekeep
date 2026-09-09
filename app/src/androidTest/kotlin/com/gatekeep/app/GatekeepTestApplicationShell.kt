package com.gatekeep.app

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager

/**
 * Non-@HiltAndroidApp base for [@CustomTestApplication] instrumented tests.
 * Provides WorkManager configuration that [GatekeepApplication] normally supplies.
 */
open class GatekeepTestApplicationShell : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        WorkManager.initialize(this, workManagerConfiguration)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
