package com.gatekeep.app.enforcement

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SamePackageReevaluationPolicyTest {

    private val blocked = "com.test.blocked"

    @Test
    fun shouldNotReevaluateWhenBlockHiddenForOtherApp() {
        assertFalse(
            SamePackageReevaluationPolicy.shouldReevaluateSamePackageCommit(
                packageName = blocked,
                currentForegroundPackage = blocked,
                blockedPackage = blocked,
                blockingActive = true,
                presentation = BlockPresentation.HiddenForOtherApp(blocked, 1L),
                sessionStartedForPackage = blocked,
                openGatePassedPackage = null,
            ),
        )
    }

    @Test
    fun shouldReevaluateWhenOpenGateNotPassedAndNotHidden() {
        assertTrue(
            SamePackageReevaluationPolicy.shouldReevaluateSamePackageCommit(
                packageName = blocked,
                currentForegroundPackage = blocked,
                blockedPackage = null,
                blockingActive = false,
                presentation = BlockPresentation.None,
                sessionStartedForPackage = blocked,
                openGatePassedPackage = null,
            ),
        )
    }
}
