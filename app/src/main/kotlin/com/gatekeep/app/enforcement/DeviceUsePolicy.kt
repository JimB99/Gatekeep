package com.gatekeep.app.enforcement

/**
 * Device use vs lock/screen-off vs having left the app.
 *
 * Pause is not leave: lock must freeze the session clock (RES-04 pocket timeout) without
 * starting the 60s away-grace. SCREEN_ON onto the keyguard is still Paused (RES-05);
 * only unlock is Using and may re-run open gate / HUD.
 */
enum class DeviceUsePhase {
    Using,
    Paused,
    Left,
}

object DeviceUsePolicy {

    fun phase(
        screenOn: Boolean,
        keyguardLocked: Boolean,
        hasLeftApp: Boolean,
    ): DeviceUsePhase {
        if (hasLeftApp) return DeviceUsePhase.Left
        if (!screenOn || keyguardLocked) return DeviceUsePhase.Paused
        return DeviceUsePhase.Using
    }

    /**
     * Open gate / HUD re-evaluate only when the user actually unlocks into the app.
     * SCREEN_ON while keyguard is locked is Paused → Paused and must not evaluate.
     */
    fun shouldEvaluateOnResume(previous: DeviceUsePhase, next: DeviceUsePhase): Boolean =
        previous == DeviceUsePhase.Paused && next == DeviceUsePhase.Using

    /** Lock/screen-off must not overlay or burn remaining. Leave still evaluates so home can hide. */
    fun shouldRunEnforcement(phase: DeviceUsePhase): Boolean = phase != DeviceUsePhase.Paused

    fun shouldFreezeSessionClock(phase: DeviceUsePhase): Boolean =
        phase == DeviceUsePhase.Paused || phase == DeviceUsePhase.Left

    /** Live lock/screen-off pauses the session clock only while still in the app. */
    fun shouldPauseSessionClock(livePaused: Boolean, hasLeftApp: Boolean): Boolean =
        livePaused && !hasLeftApp

    /**
     * Resume leftover pause markers when the user is actually using the app.
     * Stale prefs after a missed SCREEN_ON must not keep the session frozen.
     */
    fun shouldResumeSessionClock(
        livePaused: Boolean,
        hasLeftApp: Boolean,
        hasPauseMarker: Boolean,
    ): Boolean = !livePaused && !hasLeftApp && hasPauseMarker
}
