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
}
