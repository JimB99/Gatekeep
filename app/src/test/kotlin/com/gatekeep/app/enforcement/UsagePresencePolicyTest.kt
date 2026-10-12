package com.gatekeep.app.enforcement

import com.gatekeep.app.util.ForegroundEventType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsagePresencePolicyTest {

    @Test
    fun unknownEvents_countAsStillForeground() {
        assertTrue(UsagePresencePolicy.isStillForeground(null))
    }

    @Test
    fun resumed_isStillForeground() {
        assertTrue(UsagePresencePolicy.isStillForeground(ForegroundEventType.RESUMED))
    }

    @Test
    fun paused_isALeave() {
        assertFalse(UsagePresencePolicy.isStillForeground(ForegroundEventType.PAUSED))
    }
}
