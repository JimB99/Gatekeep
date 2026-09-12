package com.gatekeep.app

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.gatekeep.app.enforcement.EnforcementCoordinator
import com.gatekeep.app.ui.GatekeepNavHost
import com.gatekeep.app.ui.Routes
import com.gatekeep.app.ui.onboarding.OnboardingScreen
import com.gatekeep.app.ui.viewmodel.SettingsViewModel
import com.gatekeep.app.ui.lock.AppLockController
import com.gatekeep.app.ui.lock.AppLockScreen
import com.gatekeep.app.ui.theme.GatekeepTheme
import com.gatekeep.app.util.LocaleController
import com.gatekeep.app.util.PermissionHelper
import com.gatekeep.data.locale.LocalePreferences
import com.gatekeep.app.worker.UsageSyncWorker
import com.gatekeep.app.worker.WeeklyReportWorker
import com.gatekeep.data.repository.ProfileRepository
import com.gatekeep.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var coordinator: EnforcementCoordinator
    @Inject lateinit var profileRepository: ProfileRepository

    override fun attachBaseContext(newBase: Context) {
        LocaleController.apply(LocalePreferences.read(newBase))
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        runBlocking {
            settingsRepository.ensureLocalePersisted()
            LocaleController.apply(settingsRepository.currentLanguageTag())
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            val profiles = profileRepository.observeProfiles().first()
            if (profiles.isEmpty()) {
                val id = profileRepository.createProfile(getString(R.string.default_profile_name))
                profileRepository.toggleProfileActive(id, true)
            }
        }

        UsageSyncWorker.schedule(this)
        lifecycleScope.launch {
            val settings = settingsRepository.settings.first()
            WeeklyReportWorker.schedule(this@MainActivity, settings)
        }

        setContent {
            val settings by settingsRepository.settings.collectAsState(
                initial = com.gatekeep.data.repository.AppSettings(),
            )
            LaunchedEffect(settings.languageTag) {
                LocaleController.apply(settings.languageTag)
            }
            val lockRequired = AppLockController.isLockRequired(settings)
            var sessionUnlocked by remember { mutableStateOf(false) }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, lockRequired) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP &&
                        AppLockController.shouldLockOnStop(
                            lockRequired = lockRequired,
                            isChangingConfigurations = this@MainActivity.isChangingConfigurations,
                        )
                    ) {
                        sessionUnlocked = false
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            val showLockScreen = AppLockController.shouldShowLockScreen(
                lockRequired = lockRequired,
                sessionUnlocked = sessionUnlocked,
            )
            val settingsViewModel: SettingsViewModel = hiltViewModel()

            GatekeepTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (showLockScreen) {
                        AppLockScreen(passwordHash = settings.appPasswordHash) {
                            sessionUnlocked = true
                        }
                    } else if (!settings.onboardingComplete) {
                        OnboardingScreen(
                            onComplete = { coordinator.startEnforcementService() },
                            onFinishOnboarding = {
                                val optedIn = PermissionHelper.isAccessibilityEnabled(this@MainActivity)
                                settingsRepository.updateSettings {
                                    it.copy(
                                        onboardingComplete = true,
                                        accessibilityOptedIn = optedIn || it.accessibilityOptedIn,
                                    )
                                }
                            },
                            viewModel = settingsViewModel,
                        )
                    } else {
                        GatekeepNavHost(
                            startDestination = Routes.DASHBOARD,
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
