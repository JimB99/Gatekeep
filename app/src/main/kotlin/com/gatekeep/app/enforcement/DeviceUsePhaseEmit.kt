package com.gatekeep.app.enforcement

/**
 * Maps a phase change to a single listener. Same-phase re-emits (SCREEN_OFF + display
 * listener racing) must not fire Paused/Using twice.
 */
object DeviceUsePhaseEmit {

    enum class Notify { Paused, BecameUsing }

    fun notify(previous: DeviceUsePhase, next: DeviceUsePhase): Notify? {
        if (previous == next) return null
        if (DeviceUsePolicy.shouldEvaluateOnResume(previous, next)) return Notify.BecameUsing
        if (next == DeviceUsePhase.Paused) return Notify.Paused
        return null
    }
}
