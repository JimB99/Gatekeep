package com.gatekeep.app

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.gatekeep.app.util.LocaleController
import com.gatekeep.data.locale.LocalePreferences
import com.gatekeep.data.repository.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class GatekeepApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun attachBaseContext(base: Context) {
        LocaleController.apply(LocalePreferences.read(base))
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()
        runCatching {
            runBlocking {
                val languageTag = settingsRepository.settings.first().languageTag
                LocaleController.apply(languageTag)
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
