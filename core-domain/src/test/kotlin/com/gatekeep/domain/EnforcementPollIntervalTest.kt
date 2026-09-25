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

    @Test
    fun `session display keeps full precision on fine poll`() {
        assertEquals(
            90_000L,
            EnforcementPollInterval.sessionDisplayRemainingMs(
                90_000L,
                EnforcementPollInterval.FINE_INTERVAL_MS,
            ),
        )
    }

    @Test
    fun `session display floors partial minute on coarse poll`() {
        assertEquals(
            14 * 60_000L,
            EnforcementPollInterval.sessionDisplayRemainingMs(
                14 * 60_000L + 21_000L,
                EnforcementPollInterval.COARSE_INTERVAL_MS,
            ),
        )
    }

    @Test
    fun `session display keeps exact minute on coarse poll`() {
        assertEquals(
            45 * 60_000L,
            EnforcementPollInterval.sessionDisplayRemainingMs(
                45 * 60_000L,
                EnforcementPollInterval.COARSE_INTERVAL_MS,
            ),
        )
    }

    @Test
    fun `session display floors hour minute seconds on coarse poll`() {
        assertEquals(
            (60 + 5) * 60_000L,
            EnforcementPollInterval.sessionDisplayRemainingMs(
                (60 + 5) * 60_000L + 30_000L,
                EnforcementPollInterval.COARSE_INTERVAL_MS,
            ),
        )
    }
}
