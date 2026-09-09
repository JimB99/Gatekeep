package com.gatekeep.app.ui.lock

import com.gatekeep.data.repository.AppSettings

/**
 * Pure lock-session rules for Gatekeep's app PIN screen.
 * Extracted from [com.gatekeep.app.MainActivity] for unit testing.
 */
object AppLockController {

    fun isLockRequired(settings: AppSettings): Boolean =
        settings.appLockEnabled && settings.hasAppPin()

    fun shouldShowLockScreen(lockRequired: Boolean, sessionUnlocked: Boolean): Boolean =
        lockRequired && !sessionUnlocked

    fun shouldLockOnStop(lockRequired: Boolean, isChangingConfigurations: Boolean): Boolean =
        lockRequired && !isChangingConfigurations
}
