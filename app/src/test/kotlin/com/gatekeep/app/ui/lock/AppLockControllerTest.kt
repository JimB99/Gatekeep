package com.gatekeep.app.ui.lock

import com.gatekeep.app.util.PasswordHasher
import com.gatekeep.data.repository.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockControllerTest {

    private fun lockedSettings(pin: String = "1234") = AppSettings(
        onboardingComplete = true,
        appLockEnabled = true,
        appPasswordHash = PasswordHasher.hash(pin),
    )

    private fun lockDisabledWithPin(pin: String = "1234") = AppSettings(
        onboardingComplete = true,
        appLockEnabled = false,
        appPasswordHash = PasswordHasher.hash(pin),
    )

    private fun noPinSettings() = AppSettings(
        onboardingComplete = true,
        appLockEnabled = true,
        appPasswordHash = null,
    )

    @Test
    fun pin08_lockDisabled_doesNotRequireLock() {
        val settings = lockDisabledWithPin()
        assertFalse(AppLockController.isLockRequired(settings))
        assertFalse(
            AppLockController.shouldShowLockScreen(
                lockRequired = false,
                sessionUnlocked = false,
            ),
        )
    }

    @Test
    fun pin09_noPin_doesNotRequireLock() {
        val settings = noPinSettings()
        assertFalse(AppLockController.isLockRequired(settings))
    }

    @Test
    fun pin01_lockEnabled_requiresLockUntilSessionUnlock() {
        val settings = lockedSettings()
        assertTrue(AppLockController.isLockRequired(settings))
        assertTrue(
            AppLockController.shouldShowLockScreen(
                lockRequired = true,
                sessionUnlocked = false,
            ),
        )
        assertFalse(
            AppLockController.shouldShowLockScreen(
                lockRequired = true,
                sessionUnlocked = true,
            ),
        )
    }

    @Test
    fun pin04_onStop_locksWhenRequired() {
        assertTrue(
            AppLockController.shouldLockOnStop(
                lockRequired = true,
                isChangingConfigurations = false,
            ),
        )
    }

    @Test
    fun pin07_rotation_doesNotLockOnStop() {
        assertFalse(
            AppLockController.shouldLockOnStop(
                lockRequired = true,
                isChangingConfigurations = true,
            ),
        )
    }

    @Test
    fun pin04_onStop_skipsWhenLockNotRequired() {
        assertFalse(
            AppLockController.shouldLockOnStop(
                lockRequired = false,
                isChangingConfigurations = false,
            ),
        )
    }
}
