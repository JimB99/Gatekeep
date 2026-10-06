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

    fun shouldFreezeSessionClock(phase: DeviceUsePhase): Boolean =
        phase == DeviceUsePhase.Paused || phase == DeviceUsePhase.Left
}
