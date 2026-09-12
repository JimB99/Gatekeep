package com.gatekeep.app.enforcement

import com.gatekeep.domain.model.UsageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HudUsageDisplayTest {

    @Test
    fun `returns null when package is missing`() {
        assertNull(
            HudUsageDisplay.liveSnapshot(
                packageName = null,
                sharedPool = false,
                monitoredPackages = emptyList(),
                statsForPackage = { UsageSnapshot() },
            ),
        )
    }

    @Test
    fun `per app HUD uses stats for foreground package only`() {
        val snapshot = HudUsageDisplay.liveSnapshot(
            packageName = "com.example.a",
            sharedPool = false,
            monitoredPackages = listOf("com.example.a"),
            statsForPackage = { pkg ->
                when (pkg) {
                    "com.example.a" -> UsageSnapshot(dailyMs = 14 * 60_000L)
                    else -> UsageSnapshot(dailyMs = 99 * 60_000L)
                }
            },
        )
        assertEquals(14 * 60_000L, snapshot?.dailyMs)
    }

    @Test
    fun `shared pool HUD sums monitored app stats`() {
        val snapshot = HudUsageDisplay.liveSnapshot(
            packageName = "com.example.a",
            sharedPool = true,
            monitoredPackages = listOf("com.example.a", "com.example.b"),
            statsForPackage = { pkg ->
                when (pkg) {
                    "com.example.a" -> UsageSnapshot(dailyMs = 14 * 60_000L)
                    "com.example.b" -> UsageSnapshot(dailyMs = 6 * 60_000L)
                    else -> UsageSnapshot()
                }
            },
        )
        assertEquals(20 * 60_000L, snapshot?.dailyMs)
    }

    @Test
    fun `shared pool HUD ignores inflated persisted totals path`() {
        val snapshot = HudUsageDisplay.liveSnapshot(
            packageName = "com.example.a",
            sharedPool = false,
            monitoredPackages = listOf("com.example.a"),
            statsForPackage = { UsageSnapshot(dailyMs = 14 * 60_000L) },
        )
        assertEquals(14 * 60_000L, snapshot?.dailyMs)
    }
}
