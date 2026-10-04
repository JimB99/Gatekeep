package com.gatekeep.domain

import com.gatekeep.domain.model.SessionState

object SessionStartDecision {

    enum class Action {
        StartFresh,
        Continue,
        ResumeAfterGap,
    }

    fun decide(
        existingState: SessionState?,
        nowEpochMs: Long,
        dayStartEpochMs: Long,
        liveInMemorySession: Boolean,
        pendingWait: Boolean = false,
        onBreak: Boolean = false,
        graceMs: Long = SessionContinuityPolicy.RESUME_GRACE_MS,
    ): Action {
        if (existingState == null) return Action.StartFresh
        if (pendingWait || onBreak) return Action.Continue
        if (liveInMemorySession) {
            if (existingState.lastForegroundEndEpochMs == null) return Action.Continue
            return if (SessionContinuityPolicy.shouldContinueSession(
                    existingState.lastForegroundEndEpochMs,
                    nowEpochMs,
                    graceMs,
                )
            ) {
                Action.ResumeAfterGap
            } else {
                Action.StartFresh
            }
        }
        if (existingState.sessionStartEpochMs < dayStartEpochMs) return Action.StartFresh
        return if (SessionContinuityPolicy.shouldContinueSession(
                existingState.lastForegroundEndEpochMs,
                nowEpochMs,
                graceMs,
            )
        ) {
            Action.ResumeAfterGap
        } else {
            Action.StartFresh
        }
    }
}
