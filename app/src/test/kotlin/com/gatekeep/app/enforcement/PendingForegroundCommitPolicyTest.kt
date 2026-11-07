package com.gatekeep.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingForegroundCommitPolicyTest {

    private val yt = "app.revanced.android.youtube"
    private val launcher = "com.sec.android.app.launcher"
    private val monitored = setOf(yt)

    @Test
    fun effectiveHoldMs_openingFromLauncherWithStaleInMemoryPass_isZero() {
        assertEquals(
            0L,
            PendingForegroundCommitPolicy.effectiveHoldMs(
                routeHoldMs = 500L,
                packageName = yt,
                monitoredPackages = monitored,
                currentForegroundPackage = launcher,
                openGatePassedPackage = yt,
            ),
        )
    }

    @Test
    fun effectiveHoldMs_inAppWithMatchingPass_usesRouteHold() {
        assertEquals(
            500L,
            PendingForegroundCommitPolicy.effectiveHoldMs(
                routeHoldMs = 500L,
                packageName = yt,
                monitoredPackages = monitored,
                currentForegroundPackage = yt,
                openGatePassedPackage = yt,
            ),
        )
    }

    @Test
    fun effectiveHoldMs_inAppWithoutPass_isZero() {
        assertEquals(
            0L,
            PendingForegroundCommitPolicy.effectiveHoldMs(
                routeHoldMs = 500L,
                packageName = yt,
                monitoredPackages = monitored,
                currentForegroundPackage = yt,
                openGatePassedPackage = null,
            ),
        )
    }

    @Test
    fun effectiveHoldMs_notMonitored_usesRouteHold() {
        assertEquals(
            500L,
            PendingForegroundCommitPolicy.effectiveHoldMs(
                routeHoldMs = 500L,
                packageName = "com.whatsapp",
                monitoredPackages = monitored,
                currentForegroundPackage = launcher,
                openGatePassedPackage = null,
            ),
        )
    }

    @Test
    fun rescheduleHoldMs_neverLengthens() {
        assertEquals(0L, PendingForegroundCommitPolicy.rescheduleHoldMs(500L, 0L))
        assertEquals(300L, PendingForegroundCommitPolicy.rescheduleHoldMs(500L, 300L))
        assertEquals(500L, PendingForegroundCommitPolicy.rescheduleHoldMs(500L, 500L))
    }

    @Test
    fun forceCommitNow_afterCap_whenMonitored() {
        val firstSeen = 1_000L
        assertFalse(
            PendingForegroundCommitPolicy.forceCommitNow(
                candidatePackage = yt,
                monitoredPackages = monitored,
                candidateFirstSeenMs = firstSeen,
                nowMs = firstSeen + PendingForegroundCommitPolicy.MONITORED_MAX_PENDING_MS - 1,
            ),
        )
        assertTrue(
            PendingForegroundCommitPolicy.forceCommitNow(
                candidatePackage = yt,
                monitoredPackages = monitored,
                candidateFirstSeenMs = firstSeen,
                nowMs = firstSeen + PendingForegroundCommitPolicy.MONITORED_MAX_PENDING_MS,
            ),
        )
    }

    @Test
    fun shouldClearStaleInMemoryOpenGatePass_whenPassWithoutLiveSession() {
        assertTrue(
            PendingForegroundCommitPolicy.shouldClearStaleInMemoryOpenGatePass(
                candidatePackage = yt,
                openGatePassedPackage = yt,
                sessionStartedForPackage = null,
            ),
        )
        assertTrue(
            PendingForegroundCommitPolicy.shouldClearStaleInMemoryOpenGatePass(
                candidatePackage = yt,
                openGatePassedPackage = yt,
                sessionStartedForPackage = launcher,
            ),
        )
    }

    @Test
    fun shouldClearStaleInMemoryOpenGatePass_whenLiveSession_false() {
        assertFalse(
            PendingForegroundCommitPolicy.shouldClearStaleInMemoryOpenGatePass(
                candidatePackage = yt,
                openGatePassedPackage = yt,
                sessionStartedForPackage = yt,
            ),
        )
        assertFalse(
            PendingForegroundCommitPolicy.shouldClearStaleInMemoryOpenGatePass(
                candidatePackage = yt,
                openGatePassedPackage = null,
                sessionStartedForPackage = null,
            ),
        )
    }

    @Test
    fun debounceConfirmed_afterHold() {
        val firstSeen = 1_000L
        assertFalse(
            PendingForegroundCommitPolicy.debounceConfirmed(
                candidateFirstSeenMs = firstSeen,
                nowMs = firstSeen + ForegroundStabilizationPolicy.DEBOUNCE_MS - 1,
                debounceMs = ForegroundStabilizationPolicy.DEBOUNCE_MS,
            ),
        )
        assertTrue(
            PendingForegroundCommitPolicy.debounceConfirmed(
                candidateFirstSeenMs = firstSeen,
                nowMs = firstSeen + ForegroundStabilizationPolicy.DEBOUNCE_MS,
                debounceMs = ForegroundStabilizationPolicy.DEBOUNCE_MS,
            ),
        )
    }

    @Test
    fun forceCommitNow_notMonitored_isFalse() {
        assertFalse(
            PendingForegroundCommitPolicy.forceCommitNow(
                candidatePackage = "com.whatsapp",
                monitoredPackages = monitored,
                candidateFirstSeenMs = 0L,
                nowMs = 10_000L,
            ),
        )
    }
}
