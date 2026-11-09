package com.gatekeep.domain

import com.gatekeep.domain.model.SessionState

/** Idempotent friction start for the current sitting (home/recents restore must not reset the clock). */
object SessionFrictionStartPolicy {

    fun startFrictionIfNeeded(session: SessionState, nowEpochMs: Long): SessionState? {
        val started = session.frictionStartedAtEpochMs
        if (started != null && started >= session.sessionStartEpochMs) {
            return null
        }
        return SessionTracker.startFriction(session, nowEpochMs)
    }
}
