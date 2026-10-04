package com.gatekeep.app.enforcement

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RES-01: screen-on must re-evaluate monitored foreground even when the package did not change.
 */
class MonitoredForegroundResumePolicyTest {

    private val monitored = setOf("com.example.app")
    private val now = 1_000_000L

    @Test
    fun forceEvaluate_whenTargetIsMonitored() {
        assertTrue(
            MonitoredForegroundResumePolicy.shouldForceEvaluateOnScreenResume(
                MonitoredForegroundResumePolicy.Input(
                    targetPackage = "com.example.app",
                    monitoredPackages = monitored,
                    lastMonitoredPackage = null,
                    lastMonitoredAtMs = 0L,
                    nowMs = now,
                ),
            ),
        )
    }

    @Test
    fun skipEvaluate_whenTargetNullOrNotMonitored() {
        assertFalse(
            MonitoredForegroundResumePolicy.shouldForceEvaluateOnScreenResume(
                MonitoredForegroundResumePolicy.Input(
                    targetPackage = null,
                    monitoredPackages = monitored,
                    lastMonitoredPackage = null,
                    lastMonitoredAtMs = 0L,
                    nowMs = now,
                ),
            ),
        )
        assertFalse(
            MonitoredForegroundResumePolicy.shouldForceEvaluateOnScreenResume(
                MonitoredForegroundResumePolicy.Input(
                    targetPackage = "com.android.launcher",
                    monitoredPackages = monitored,
                    lastMonitoredPackage = null,
                    lastMonitoredAtMs = 0L,
                    nowMs = now,
                ),
            ),
        )
    }

    @Test
    fun forceEvaluate_whenRecentLastMonitoredMatchesTarget() {
        assertTrue(
            MonitoredForegroundResumePolicy.shouldForceEvaluateOnScreenResume(
                MonitoredForegroundResumePolicy.Input(
                    targetPackage = "com.example.app",
                    monitoredPackages = emptySet(),
                    lastMonitoredPackage = "com.example.app",
                    lastMonitoredAtMs = now - 30_000L,
                    nowMs = now,
                ),
            ),
        )
    }

    @Test
    fun skipEvaluate_whenLastMonitoredTooOld() {
        assertFalse(
            MonitoredForegroundResumePolicy.shouldForceEvaluateOnScreenResume(
                MonitoredForegroundResumePolicy.Input(
                    targetPackage = "com.example.app",
                    monitoredPackages = emptySet(),
                    lastMonitoredPackage = "com.example.app",
                    lastMonitoredAtMs = now - MonitoredForegroundResumePolicy.DEFAULT_LAST_MONITORED_WINDOW_MS - 1,
                    nowMs = now,
                ),
            ),
        )
    }
}
