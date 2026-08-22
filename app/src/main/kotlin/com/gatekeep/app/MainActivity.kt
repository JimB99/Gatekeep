package com.gatekeep.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.gatekeep.app.enforcement.EnforcementCoordinator
import com.gatekeep.app.ui.GatekeepNavHost
import com.gatekeep.app.ui.Routes
import com.gatekeep.app.ui.lock.AppLockScreen
import com.gatekeep.app.ui.theme.GatekeepTheme
import com.gatekeep.app.worker.UsageSyncWorker
import com.gatekeep.app.worker.WeeklyReportWorker
import com.gatekeep.data.repository.ProfileRepository
import com.gatekeep.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var coordinator: EnforcementCoordinator
    @Inject lateinit var profileRepository: ProfileRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            val profiles = profileRepository.observeProfiles().first()
            if (profiles.isEmpty()) {
                val id = profileRepository.createProfile("Default")
                profileRepository.activateProfile(id)
            }
        }

        UsageSyncWorker.schedule(this)
        WeeklyReportWorker.schedule(this)

        setContent {
            val settings by settingsRepository.settings.collectAsState(
                initial = com.gatekeep.data.repository.AppSettings(),
            )
            var unlocked by remember { mutableStateOf(!settings.appLockEnabled || settings.appPasswordHash == null) }
            val startDest = if (settings.onboardingComplete) Routes.DASHBOARD else Routes.ONBOARDING

            GatekeepTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!unlocked && settings.appLockEnabled) {
                        AppLockScreen(passwordHash = settings.appPasswordHash) { unlocked = true }
                    } else {
                        GatekeepNavHost(
                            startDestination = startDest,
                            onEnforcementStart = { coordinator.startEnforcementService() },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        coordinator.refresh()
    }
}
