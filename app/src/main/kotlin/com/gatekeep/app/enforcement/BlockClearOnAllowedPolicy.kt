package com.gatekeep.app.enforcement

import com.gatekeep.domain.model.BlockReason
import com.gatekeep.domain.model.RuleEvaluation
import com.gatekeep.domain.model.RuleResult

/**
 * Gates tearing down block presentation when [RuleEngine] returns [RuleResult.Allowed].
 *
 * Clearing block while a limit axis is still [RuleResult.Blocked] left the app usable with a
 * stale HUD after returning from home (RES-02 / RES-03).
 */
object BlockClearOnAllowedPolicy {

    /** True when any usage axis still requires a block overlay. */
    fun evaluationStillRequiresBlock(evaluation: RuleEvaluation): Boolean {
        if (evaluation.session is RuleResult.Blocked) return true
        val period = evaluation.period as? RuleResult.Blocked ?: return false
        val remainingDailyMs = evaluation.remainingDailyMs
        if (period.reason == BlockReason.dailyLimit && remainingDailyMs != null && remainingDailyMs > 0L) {
            return false
        }
        return true
    }

    /**
     * @return whether [clearBlockState] is safe for this allowed evaluation on [packageName].
     */
    fun shouldClearBlockOnAllowed(
        evaluation: RuleEvaluation,
        blockingActive: Boolean,
        blockedPackage: String?,
        packageName: String,
        overlayVisible: Boolean,
    ): Boolean {
        if (evaluationStillRequiresBlock(evaluation)) return false
        if (!blockingActive && !overlayVisible) return false
        if (blockedPackage != null && blockedPackage != packageName) return false
        return true
    }
}
