package com.gatekeep.domain

import com.gatekeep.domain.model.BlockReason
import com.gatekeep.domain.model.LimitUsageScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ExtensionGrantEngineTest {

    @Test
    fun `overlay session block adds session excluded only`() {
        val plan = ExtensionGrantEngine.planGrant(
            profileId = 1L,
            packageName = "com.test",
            minutes = 10,
            nowEpochMs = 1_000_000L,
            limitUsageScope = LimitUsageScope.perApp,
            blockedReason = BlockReason.sessionLimit,
            source = ExtensionGrantSource.overlay,
        )
        assertEquals(10 * 60_000L, plan.sessionExcludedMsDelta)
        assertNull(plan.graceUntilEpochMs)
    }

    @Test
    fun `overlay daily block adds grace for per app`() {
        val plan = ExtensionGrantEngine.planGrant(
            profileId = 1L,
            packageName = "com.test",
            minutes = 5,
            nowEpochMs = 1_000_000L,
            limitUsageScope = LimitUsageScope.perApp,
            blockedReason = BlockReason.dailyLimit,
            source = ExtensionGrantSource.overlay,
        )
        assertEquals(0L, plan.sessionExcludedMsDelta)
        assertEquals(1L, plan.graceProfileId)
        assertEquals("com.test", plan.gracePackageName)
        assertEquals(1_000_000L + 5 * 60_000L, plan.graceUntilEpochMs)
    }

    @Test
    fun `overlay daily block shared pool uses profile grace`() {
        val plan = ExtensionGrantEngine.planGrant(
            profileId = 2L,
            packageName = "com.test",
            minutes = 5,
            nowEpochMs = 0L,
            limitUsageScope = LimitUsageScope.sharedPool,
            blockedReason = BlockReason.hourlyLimit,
            source = ExtensionGrantSource.overlay,
        )
        assertEquals(2L, plan.graceProfileId)
        assertNull(plan.gracePackageName)
    }

    @Test
    fun `in app proactive grants session and period grace`() {
        val plan = ExtensionGrantEngine.planGrant(
            profileId = 1L,
            packageName = "com.test",
            minutes = 10,
            nowEpochMs = 100L,
            limitUsageScope = LimitUsageScope.sharedPool,
            blockedReason = null,
            source = ExtensionGrantSource.inApp,
        )
        assertEquals(10 * 60_000L, plan.sessionExcludedMsDelta)
        assertEquals(100L + 10 * 60_000L, plan.graceUntilEpochMs)
        assertNull(plan.gracePackageName)
    }
}
