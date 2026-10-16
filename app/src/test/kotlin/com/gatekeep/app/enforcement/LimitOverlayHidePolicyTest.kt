package com.gatekeep.app.enforcement

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RES-02: temporary hide must reset friction guard so block overlay can be shown again.
 */
class LimitOverlayHidePolicyTest {

    @Test
    fun frictionFlagFalseAfterTemporaryHide() {
        assertFalse(LimitOverlayHidePolicy.frictionInProgressAfterTemporaryHide())
    }

    @Test
    fun hidePosted_stillVisibleUntilRemoveCompletes() {
        assertTrue(LimitOverlayHidePolicy.overlayVisibleAfterHideRequested(removeCompleted = false))
        assertFalse(LimitOverlayHidePolicy.overlayVisibleAfterHideRequested(removeCompleted = true))
    }

    @Test
    fun hideRequested_mustNotEatClicks() {
        assertFalse(LimitOverlayHidePolicy.consumeTouches(hideRequested = true))
        assertTrue(LimitOverlayHidePolicy.consumeTouches(hideRequested = false))
    }
}
