package com.gatekeep.app.enforcement

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundTransitionPolicyTest {

    @Test
    fun `first detection processes`() {
        assertTrue(
            ForegroundTransitionPolicy.shouldProcessThirdPartyForegroundChange(
                incomingPackage = "com.instagram.android",
                currentForegroundPackage = null,
            ),
        )
    }

    @Test
    fun `package change processes`() {
        assertTrue(
            ForegroundTransitionPolicy.shouldProcessThirdPartyForegroundChange(
                incomingPackage = "com.instagram.android",
                currentForegroundPackage = "com.android.launcher",
            ),
        )
    }

    @Test
    fun `same package ignored`() {
        assertFalse(
            ForegroundTransitionPolicy.shouldProcessThirdPartyForegroundChange(
                incomingPackage = "com.instagram.android",
                currentForegroundPackage = "com.instagram.android",
            ),
        )
    }
}
