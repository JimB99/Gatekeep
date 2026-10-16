package com.gatekeep.domain

import com.gatekeep.domain.model.SessionState

/**
 * Persists state when the user completes on-open friction (wait/math/PIN).
 * Pending wait must be cleared before the next [evaluate] — otherwise evaluate
 * treats the session as mid-wait and can show a second overlay (open-gate bug).
 * Away timestamps must also be cleared: a leftover lastForegroundEnd beyond
 * session continuity grace makes [SessionStartDecision] StartFresh, which wipes
 * [openGatePassedPackage] and flashes the wait overlay again (e15).
 */
object OpenGateCompletionPolicy {

    fun sessionAfterOpenGatePassed(session: SessionState, nowEpochMs: Long): SessionState =
        OpenGatePassPolicy.markOpenGatePassed(
            SessionTracker.clearPendingWait(SessionTracker.endFriction(session, nowEpochMs)).copy(
                lastForegroundEndEpochMs = null,
                devicePausedAtEpochMs = null,
            ),
            nowEpochMs,
        )
}
