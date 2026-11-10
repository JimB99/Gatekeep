package com.gatekeep.app.enforcement

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostLongPauseUnlockBoostPolicyTest {

    private val now = 10_000_000L
    private val oneHour = PostLongPauseUnlockBoostPolicy.LONG_DEVICE_PAUSE_MS

    @Test
    fun shouldStartBoost_whenLongPauseAndUnlockSkipped() {
        assertTrue(
            PostLongPauseUnlockBoostPolicy.shouldStartBoost(
                pauseDurationMs = oneHour,
                unlockSkipped = true,
            ),
        )
    }

    @Test
    fun shouldNotStartBoost_whenUnlockResumed() {
        assertFalse(
            PostLongPauseUnlockBoostPolicy.shouldStartBoost(
                pauseDurationMs = oneHour,
                unlockSkipped = false,
            ),
        )
    }

    @Test
    fun shouldNotStartBoost_whenPauseUnderOneHour() {
        assertFalse(
            PostLongPauseUnlockBoostPolicy.shouldStartBoost(
                pauseDurationMs = oneHour - 1,
                unlockSkipped = true,
            ),
        )
    }

    @Test
    fun boostActive_forNinetySeconds() {
        val start = now
        assertTrue(PostLongPauseUnlockBoostPolicy.boostActive(start + 1, start))
        assertTrue(
            PostLongPauseUnlockBoostPolicy.boostActive(
                start + PostLongPauseUnlockBoostPolicy.BOOST_DURATION_MS - 1,
                start,
            ),
        )
        assertFalse(
            PostLongPauseUnlockBoostPolicy.boostActive(
                start + PostLongPauseUnlockBoostPolicy.BOOST_DURATION_MS,
                start,
            ),
        )
    }

    @Test
    fun shouldScheduleNextReconcile_respectsInterval() {
        val start = now
        val last = start
        assertFalse(
            PostLongPauseUnlockBoostPolicy.shouldScheduleNextReconcile(
                nowEpochMs = start + 100,
                boostStartedAtEpochMs = start,
                lastReconcileAtEpochMs = last,
            ),
        )
        assertTrue(
            PostLongPauseUnlockBoostPolicy.shouldScheduleNextReconcile(
                nowEpochMs = start + PostLongPauseUnlockBoostPolicy.BOOST_RECONCILE_INTERVAL_MS,
                boostStartedAtEpochMs = start,
                lastReconcileAtEpochMs = last,
            ),
        )
    }
}
