package com.gatekeep.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue 01: evaluate / overlay_show traces must include daily used, cap, and remaining
 * so a false daily block is diagnosable. Jim's Current Usage: 5h15m / 6h30m.
 */
class EnforcementDailyTraceTest {

    private val usedMs = 5 * 60 * 60_000L + 15 * 60_000L
    private val capMs = 6 * 60 * 60_000L + 30 * 60_000L
    private val remainingMs = capMs - usedMs

    @Test
    fun fields_includeUsedCapRemaining() {
        val fields = EnforcementDailyTrace.fields(
            usedMs = usedMs,
            capMs = capMs,
            remainingMs = remainingMs,
        )
        assertEquals(usedMs.toString(), fields[EnforcementDailyTrace.USED])
        assertEquals(capMs.toString(), fields[EnforcementDailyTrace.CAP])
        assertEquals(remainingMs.toString(), fields[EnforcementDailyTrace.REMAINING])
    }

    @Test
    fun shouldPresent_falseWhenDailyRemainingPositive() {
        assertFalse(EnforcementDailyTrace.shouldPresentDailyLimit(remainingMs))
        assertFalse(EnforcementDailyTrace.shouldPresentDailyLimit(1L))
    }

    @Test
    fun shouldPresent_trueWhenDailyRemainingExhaustedOrUnknown() {
        assertTrue(EnforcementDailyTrace.shouldPresentDailyLimit(0L))
        assertTrue(EnforcementDailyTrace.shouldPresentDailyLimit(-1L))
        assertTrue(EnforcementDailyTrace.shouldPresentDailyLimit(null))
    }

    @Test
    fun overlayShow_withDailyBudget_roundTripsOnExportLine() {
        val entry = EnforcementTrace.Entry(
            epochMs = 1_791_757_377_947L,
            event = EnforcementTrace.OVERLAY_SHOW,
            fields = linkedMapOf(
                "pkg" to "app.revanced.android.youtube",
                "reason" to "dailyLimit",
                "openGate" to "false",
            ) + EnforcementDailyTrace.fields(usedMs, capMs, remainingMs),
        )
        val parsed = EnforcementTraceQuery.parseLine(entry.format())
        assertEquals(entry, parsed)
        assertEquals(usedMs.toString(), parsed!!.fields[EnforcementDailyTrace.USED])
        assertEquals(capMs.toString(), parsed.fields[EnforcementDailyTrace.CAP])
        assertEquals(remainingMs.toString(), parsed.fields[EnforcementDailyTrace.REMAINING])
    }

    @Test
    fun replay_01_unlockUsagePausedDailyLimit_mustNotPresentWhileBudgetRemains() {
        val log = """
            1791757377665 unlock_resume pkg=app.revanced.android.youtube
            1791757377671 evaluate pkg=app.revanced.android.youtube
            1791757377930 session_leave pkg=app.revanced.android.youtube reason=usage_paused
            1791757377947 overlay_show pkg=app.revanced.android.youtube reason=dailyLimit openGate=false
            1791757399786 hud_freeze pkg=app.revanced.android.youtube remainingMs=106862
        """.trimIndent()
        val entries = log.lineSequence().mapNotNull { EnforcementTraceQuery.parseLine(it) }.toList()
        val overlay = entries.first { it.event == EnforcementTrace.OVERLAY_SHOW }
        assertEquals("dailyLimit", overlay.fields["reason"])
        assertEquals(
            "usage_paused",
            entries.last {
                it.epochMs <= overlay.epochMs && it.event == EnforcementTrace.SESSION_LEAVE
            }.fields["reason"],
        )
        assertFalse(
            "01 log dailyLimit after usage_paused must not present while Current Usage remaining > 0",
            EnforcementDailyTrace.shouldPresentDailyLimit(remainingMs),
        )
    }
}
