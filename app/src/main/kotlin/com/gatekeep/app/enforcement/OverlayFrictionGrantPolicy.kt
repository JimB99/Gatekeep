package com.gatekeep.app.enforcement

import com.gatekeep.domain.model.BlockPresentationReason

/**
 * Completing overlay wait/math: session refill and period continue are independent of
 * extension quota and overlay surface. Limit + extend buttons still use the extension path.
 */
object OverlayFrictionGrantPolicy {

    enum class Grant {
        OpenGate,
        SessionRefill,
        PeriodContinue,
        PeriodExtension,
    }

    fun grant(request: BlockOverlayRequest): Grant {
        if (request.isOpenGate) return Grant.OpenGate
        return when (request.reason) {
            BlockPresentationReason.sessionLimit -> Grant.SessionRefill
            BlockPresentationReason.hourlyLimit,
            BlockPresentationReason.dailyLimit,
            BlockPresentationReason.weeklyLimit,
            -> Grant.PeriodContinue
            else -> Grant.PeriodExtension
        }
    }
}
