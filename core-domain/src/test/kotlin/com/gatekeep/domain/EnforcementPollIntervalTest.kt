package com.gatekeep.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EnforcementPollIntervalTest {

    private val now = 1_000_000L

    @Test
    fun `returns null when no deadlines`() {
        assertNull(EnforcementPollInterval.enforcementLoopIntervalMs(now, emptyList()))
        assertNull(EnforcementPollInterval.enforcementLoopIntervalMs(now, listOf(null, null)))
    }

    @Test
    fun `returns coarse when more than five minutes remain`() {
        val deadline = now + 6 * 60_000L
        assertEquals(
            EnforcementPollInterval.COARSE_INTERVAL_MS,
            EnforcementPollInterval.enforcementLoopIntervalMs(now, listOf(deadline)),
        )
    }

    @Test
    fun `returns fine at exactly five minutes`() {
        val deadline = now + 5 * 60_000L
        assertEquals(
            EnforcementPollInterval.FINE_INTERVAL_MS,
            EnforcementPollInterval.enforcementLoopIntervalMs(now, listOf(deadline)),
        )
    }

    @Test
    fun `returns fine when under five minutes remain`() {
        val deadline = now + 60_000L
        assertEquals(
            EnforcementPollInterval.FINE_INTERVAL_MS,
            EnforcementPollInterval.enforcementLoopIntervalMs(now, listOf(deadline)),
        )
    }

    @Test
    fun `uses minimum remaining across multiple deadlines`() {
        val far = now + 60 * 60_000L
        val near = now + 2 * 60_000L
        assertEquals(
            EnforcementPollInterval.FINE_INTERVAL_MS,
            EnforcementPollInterval.enforcementLoopIntervalMs(now, listOf(far, near)),
        )
    }

    @Test
    fun `returns fine when deadline already passed`() {
        val passed = now - 1_000L
        assertEquals(
            EnforcementPollInterval.FINE_INTERVAL_MS,
            EnforcementPollInterval.enforcementLoopIntervalMs(now, listOf(passed)),
        )
    }
}
