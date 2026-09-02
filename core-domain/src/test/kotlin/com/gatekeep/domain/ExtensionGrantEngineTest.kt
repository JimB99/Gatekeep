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

    @Test
    fun `stacks grace onto remaining until not now`() {
        val now = 1_000_000L
        val existingUntil = now + 5 * 60_000L
        val plan = ExtensionGrantEngine.planGrant(
            profileId = 1L,
            packageName = "com.test",
            minutes = 15,
            nowEpochMs = now,
            limitUsageScope = LimitUsageScope.perApp,
            blockedReason = null,
            source = ExtensionGrantSource.inApp,
            existingGraceUntilEpochMs = existingUntil,
        )
        assertEquals(existingUntil + 15 * 60_000L, plan.graceUntilEpochMs)
    }

    @Test
    fun `expired grace stacks from now`() {
        val now = 1_000_000L
        val stacked = ExtensionGrantEngine.stackedGraceUntilEpochMs(
            nowEpochMs = now,
            extensionMs = 5 * 60_000L,
            existingGraceUntilEpochMs = now - 1,
        )
        assertEquals(now + 5 * 60_000L, stacked)
    }

    @Test
    fun `active grace until picks latest matching pause`() {
        val pauses = listOf(
            com.gatekeep.domain.model.Pause(
                profileId = 1L,
                packageName = "com.test",
                type = com.gatekeep.domain.model.PauseType.extensionGrace,
                untilEpochMs = 50L,
            ),
            com.gatekeep.domain.model.Pause(
                profileId = 1L,
                packageName = "com.test",
                type = com.gatekeep.domain.model.PauseType.extensionGrace,
                untilEpochMs = 90L,
            ),
        )
        val until = ExtensionGrantEngine.activeGraceUntilEpochMs(
            pauses = pauses,
            profileId = 1L,
            packageName = "com.test",
            nowEpochMs = 10L,
            sharedPool = false,
        )
        assertEquals(90L, until)
    }
}
