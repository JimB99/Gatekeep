package com.gatekeep.domain

import com.gatekeep.domain.model.OnOpenAction

enum class PendingWaitResumeSurface {
    OPEN_GATE,
    SESSION_LIMIT,
}

/**
 * While [SessionTracker.hasPendingWait], resume the correct overlay after unlock (RES-01/05).
 * Open-gate wait must not reuse the session-limit block UI.
 */
object PendingWaitResumePolicy {

    fun surfaceFor(onOpenAction: OnOpenAction): PendingWaitResumeSurface = when (onOpenAction) {
        OnOpenAction.deterrentWait -> PendingWaitResumeSurface.OPEN_GATE
        else -> PendingWaitResumeSurface.SESSION_LIMIT
    }

    /**
     * In-flight evaluate can still see [SessionTracker.hasPendingWait] after the wait
     * finished and open-gate was recorded — that must not flash a second wait (G-16).
     */
    fun shouldResume(
        hasPendingWait: Boolean,
        openGateAlreadyPassed: Boolean,
        overlayHasActiveWait: Boolean,
        devicePaused: Boolean,
        screenOn: Boolean,
        overlayShowInFlight: Boolean = false,
    ): Boolean {
        if (!hasPendingWait) return false
        if (openGateAlreadyPassed) return false
        if (overlayHasActiveWait || overlayShowInFlight || devicePaused || !screenOn) return false
        return true
    }
}
