package com.gatekeep.app.enforcement

import com.gatekeep.domain.model.BlockReason
import com.gatekeep.domain.model.RuleEvaluation
import com.gatekeep.domain.model.RuleResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RES-02 / RES-03: Allowed presentation must not clear an unresolved session/period block.
 */
class BlockClearOnAllowedPolicyTest {

    private val pkg = "com.example.app"

    @Test
    fun evaluationStillRequiresBlock_whenSessionOrPeriodBlocked() {
        assertTrue(
            BlockClearOnAllowedPolicy.evaluationStillRequiresBlock(
                RuleEvaluation(
                    session = RuleResult.Blocked(BlockReason.sessionLimit),
                ),
            ),
        )
        assertTrue(
            BlockClearOnAllowedPolicy.evaluationStillRequiresBlock(
                RuleEvaluation(
                    period = RuleResult.Blocked(BlockReason.dailyLimit),
                ),
            ),
        )
        assertFalse(
            BlockClearOnAllowedPolicy.evaluationStillRequiresBlock(
                RuleEvaluation(
                    session = RuleResult.Allowed(null, 60_000L, null, null),
                ),
            ),
        )
    }

    @Test
    fun shouldNotClear_whenEvaluationStillRequiresBlock() {
        assertFalse(
            BlockClearOnAllowedPolicy.shouldClearBlockOnAllowed(
                evaluation = RuleEvaluation(session = RuleResult.Blocked(BlockReason.sessionLimit)),
                blockingActive = true,
                blockedPackage = pkg,
                packageName = pkg,
                overlayVisible = false,
            ),
        )
    }

    @Test
    fun shouldClear_whenGenuinelyAllowedAndBlockActive() {
        assertTrue(
            BlockClearOnAllowedPolicy.shouldClearBlockOnAllowed(
                evaluation = RuleEvaluation(
                    session = RuleResult.Allowed(null, 5 * 60_000L, null, null),
                ),
                blockingActive = true,
                blockedPackage = pkg,
                packageName = pkg,
                overlayVisible = true,
            ),
        )
    }
}
