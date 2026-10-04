package com.gatekeep.app.enforcement

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * RES-02: temporary hide must reset friction guard so block overlay can be shown again.
 */
class LimitOverlayHidePolicyTest {

    @Test
    fun frictionFlagFalseAfterTemporaryHide() {
        assertFalse(LimitOverlayHidePolicy.frictionInProgressAfterTemporaryHide())
    }
}
