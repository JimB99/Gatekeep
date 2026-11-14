package com.gatekeep.app.enforcement

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenGateWaitCompletionPolicyTest {

    @Test
    fun openGateWaitTimer_mustNotClearPendingSeparately() {
        assertFalse(
            OpenGateWaitCompletionPolicy.shouldClearPendingOnWaitTimer(
                OverlayFrictionGrantPolicy.Grant.OpenGate,
            ),
        )
    }

    @Test
    fun limitWaitTimer_stillClearsPending() {
        assertTrue(
            OpenGateWaitCompletionPolicy.shouldClearPendingOnWaitTimer(
                OverlayFrictionGrantPolicy.Grant.PeriodContinue,
            ),
        )
        assertTrue(
            OpenGateWaitCompletionPolicy.shouldClearPendingOnWaitTimer(
                OverlayFrictionGrantPolicy.Grant.SessionRefill,
            ),
        )
    }
}