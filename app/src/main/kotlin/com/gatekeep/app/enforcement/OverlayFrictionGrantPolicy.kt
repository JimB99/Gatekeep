package com.gatekeep.app.enforcement

import com.gatekeep.domain.model.BlockPresentationReason

/**
 * Completing overlay wait/math: session refill is independent of extension quota and
 * overlay surface. Period wait/math still uses the extension path until issue 03.
 */
object OverlayFrictionGrantPolicy {

    enum class Grant {
        OpenGate,
        SessionRefill,
        PeriodExtension,
    }

    fun grant(request: BlockOverlayRequest): Grant {
        if (request.isOpenGate) return Grant.OpenGate
        return when (request.reason) {
            BlockPresentationReason.sessionLimit -> Grant.SessionRefill
            else -> Grant.PeriodExtension
        }
    }
}
