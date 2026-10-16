package com.gatekeep.app.enforcement

import com.gatekeep.domain.model.BlockPresentationReason
import com.gatekeep.domain.model.BlockReason

/**
 * Completing overlay wait/math: session refill and period continue are independent of
 * extension quota and overlay surface. Limit + extend buttons still use the extension path.
 * Unknown reasons complete friction with no time grant.
 */
object OverlayFrictionGrantPolicy {

    enum class Grant {
        OpenGate,
        SessionRefill,
        PeriodContinue,
        None,
    }

    fun grant(request: BlockOverlayRequest): Grant {
        if (request.isOpenGate) return Grant.OpenGate
        return when (request.reason) {
            BlockPresentationReason.sessionLimit -> Grant.SessionRefill
            BlockPresentationReason.hourlyLimit,
            BlockPresentationReason.dailyLimit,
            BlockPresentationReason.weeklyLimit,
            -> Grant.PeriodContinue
            else -> Grant.None
        }
    }

    fun blockReason(reason: BlockPresentationReason): BlockReason? = when (reason) {
        BlockPresentationReason.sessionLimit -> BlockReason.sessionLimit
        BlockPresentationReason.hourlyLimit -> BlockReason.hourlyLimit
        BlockPresentationReason.dailyLimit -> BlockReason.dailyLimit
        BlockPresentationReason.weeklyLimit -> BlockReason.weeklyLimit
        BlockPresentationReason.onBreak -> BlockReason.onBreak
        else -> null
    }
}
