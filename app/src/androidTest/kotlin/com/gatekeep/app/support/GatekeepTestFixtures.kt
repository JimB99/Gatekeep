package com.gatekeep.app.support

import com.gatekeep.app.util.PasswordHasher
import com.gatekeep.data.repository.AppSettings
import com.gatekeep.data.repository.SettingsRepository

object GatekeepTestFixtures {
    const val TEST_PIN = "1234"

    suspend fun seedAppLockEnabled(
        settingsRepository: SettingsRepository,
        pin: String = TEST_PIN,
        onboardingComplete: Boolean = true,
    ) {
        settingsRepository.updateSettings {
            it.copy(
                onboardingComplete = onboardingComplete,
                appLockEnabled = true,
                appPasswordHash = PasswordHasher.hash(pin),
            )
        }
    }

    suspend fun seedAppLockDisabled(
        settingsRepository: SettingsRepository,
        pin: String = TEST_PIN,
        onboardingComplete: Boolean = true,
    ) {
        settingsRepository.updateSettings {
            it.copy(
                onboardingComplete = onboardingComplete,
                appLockEnabled = false,
                appPasswordHash = PasswordHasher.hash(pin),
            )
        }
    }

    suspend fun seedNoPin(
        settingsRepository: SettingsRepository,
        onboardingComplete: Boolean = true,
    ) {
        settingsRepository.updateSettings {
            it.copy(
                onboardingComplete = onboardingComplete,
                appLockEnabled = true,
                appPasswordHash = null,
            )
        }
    }

    fun lockedSettings(pin: String = TEST_PIN): AppSettings = AppSettings(
        onboardingComplete = true,
        appLockEnabled = true,
        appPasswordHash = PasswordHasher.hash(pin),
    )

    fun lockDisabledWithPin(pin: String = TEST_PIN): AppSettings = AppSettings(
        onboardingComplete = true,
        appLockEnabled = false,
        appPasswordHash = PasswordHasher.hash(pin),
    )

    fun noPinSettings(): AppSettings = AppSettings(
        onboardingComplete = true,
        appLockEnabled = true,
        appPasswordHash = null,
    )
}
