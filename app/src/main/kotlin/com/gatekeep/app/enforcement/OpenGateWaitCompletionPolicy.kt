package com.gatekeep.app.enforcement

/**
 * Open-gate wait completion is persisted only in [EnforcementCoordinator.onOpenGatePassed]
 * ([com.gatekeep.domain.OpenGateCompletionPolicy] clears pending and records pass).
 * A separate [EnforcementCoordinator.onWaitCompleted] read/write can race and wipe
 * [com.gatekeep.domain.model.SessionState.openGatePassedEpochMs].
 */
object OpenGateWaitCompletionPolicy {

    fun shouldClearPendingOnWaitTimer(grant: OverlayFrictionGrantPolicy.Grant): Boolean =
        grant != OverlayFrictionGrantPolicy.Grant.OpenGate
}