package com.gatekeep.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionLeavePolicyTest {

    @Test
    fun packageToFinalize_usesLeavingAppNotNewlyFocused() {
        assertEquals(
            "com.youtube.android",
            SessionLeavePolicy.packageToFinalize(
                leavingPackage = "com.youtube.android",
                newlyForegroundPackage = "com.android.launcher3",
            ),
        )
    }

    @Test
    fun packageToFinalize_nullWhenLeaveUnknown_doesNotMarkNewApp() {
        assertNull(
            SessionLeavePolicy.packageToFinalize(
                leavingPackage = null,
                newlyForegroundPackage = "com.youtube.android",
            ),
        )
    }

    @Test
    fun packageToFinalize_nullWhenSamePackage() {
        assertNull(
            SessionLeavePolicy.packageToFinalize(
                leavingPackage = "com.youtube.android",
                newlyForegroundPackage = "com.youtube.android",
            ),
        )
    }
}
