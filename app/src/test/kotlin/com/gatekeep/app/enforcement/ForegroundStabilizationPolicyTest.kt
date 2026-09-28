package com.gatekeep.app.enforcement

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundStabilizationPolicyTest {

    private val ignored = setOf("com.android.systemui")

    @Test
    fun debounceConfirmed_afterHoldDuration() {
        assertFalse(
            ForegroundStabilizationPolicy.debounceConfirmed(
                candidatePackage = "com.example",
                candidateFirstSeenMs = 1_000L,
                nowMs = 1_000L + ForegroundStabilizationPolicy.DEBOUNCE_MS - 1,
            ),
        )
        assertTrue(
            ForegroundStabilizationPolicy.debounceConfirmed(
                candidatePackage = "com.example",
                candidateFirstSeenMs = 1_000L,
                nowMs = 1_000L + ForegroundStabilizationPolicy.DEBOUNCE_MS,
            ),
        )
    }

    @Test
    fun monitoredGrace_ignoresLauncherBlipWithinWindow() {
        val now = 10_000L
        assertTrue(
            ForegroundStabilizationPolicy.shouldApplyMonitoredGrace(
                incomingPackage = "com.android.launcher3",
                ignoredPackages = ignored,
                lastMonitoredPackage = "com.instagram.android",
                lastMonitoredAtMs = now - 1_000L,
                nowMs = now,
            ),
        )
    }

    @Test
    fun monitoredGrace_expiresAfterWindow() {
        val now = 10_000L
        assertFalse(
            ForegroundStabilizationPolicy.shouldApplyMonitoredGrace(
                incomingPackage = "com.android.systemui",
                ignoredPackages = ignored,
                lastMonitoredPackage = "com.instagram.android",
                lastMonitoredAtMs = now - ForegroundStabilizationPolicy.MONITORED_GRACE_MS - 1,
                nowMs = now,
            ),
        )
    }

    @Test
    fun isTransientForegroundPackage_includesCommonLaunchers() {
        assertTrue(ForegroundStabilizationPolicy.isTransientForegroundPackage("com.android.launcher3"))
        assertFalse(ForegroundStabilizationPolicy.isTransientForegroundPackage("com.instagram.android"))
    }
}
