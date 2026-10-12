package com.gatekeep.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnforcementTraceQueryTest {

    private val now = 1_000_000L

    @Test
    fun filter_lastHour_dropsOlderEvents() {
        val entries = listOf(
            EnforcementTrace.Entry(now - 3_600_000L - 1, "evaluate", mapOf("pkg" to "old")),
            EnforcementTrace.Entry(now - 60_000L, "evaluate", mapOf("pkg" to "recent")),
        )
        val since = EnforcementTraceQuery.sinceEpochMs(EnforcementTraceQuery.Window.LAST_HOUR, now)
        val text = EnforcementTraceQuery.formatExport(entries, since)
        assertFalse(text.contains("old"))
        assertTrue(text.contains("recent"))
    }

    @Test
    fun filter_all_keepsEverything() {
        val entries = listOf(
            EnforcementTrace.Entry(1L, "a", emptyMap()),
            EnforcementTrace.Entry(now, "b", emptyMap()),
        )
        val since = EnforcementTraceQuery.sinceEpochMs(EnforcementTraceQuery.Window.ALL, now)
        assertEquals(2, EnforcementTraceQuery.filter(entries, since).size)
    }

    @Test
    fun coalesce_repeatedForegroundCommit() {
        assertTrue(
            EnforcementTraceQuery.shouldCoalesce(
                lastEvent = EnforcementTrace.FOREGROUND_COMMIT,
                lastFields = mapOf("pkg" to "com.sec.android.app.launcher", "holdMs" to "500"),
                event = EnforcementTrace.FOREGROUND_COMMIT,
                fields = mapOf("pkg" to "com.sec.android.app.launcher", "holdMs" to "500"),
            ),
        )
        assertFalse(
            EnforcementTraceQuery.shouldCoalesce(
                lastEvent = EnforcementTrace.FOREGROUND_COMMIT,
                lastFields = mapOf("pkg" to "com.sec.android.app.launcher", "holdMs" to "500"),
                event = EnforcementTrace.FOREGROUND_COMMIT,
                fields = mapOf("pkg" to "com.whatsapp", "holdMs" to "500"),
            ),
        )
        assertTrue(
            EnforcementTraceQuery.shouldCoalesce(
                lastEvent = EnforcementTrace.OVERLAY_SHOW,
                lastFields = mapOf("pkg" to "yt", "reason" to "sessionLimit", "openGate" to "false"),
                event = EnforcementTrace.OVERLAY_SHOW,
                fields = mapOf("pkg" to "yt", "reason" to "sessionLimit", "openGate" to "false"),
            ),
        )
        assertFalse(
            EnforcementTraceQuery.shouldCoalesce(
                lastEvent = EnforcementTrace.EVALUATE,
                lastFields = mapOf("pkg" to "com.whatsapp"),
                event = EnforcementTrace.FOREGROUND_COMMIT,
                fields = mapOf("pkg" to "com.whatsapp", "holdMs" to "500"),
            ),
        )
        assertTrue(
            EnforcementTraceQuery.shouldCoalesce(
                lastEvent = EnforcementTrace.EVALUATE,
                lastFields = mapOf("pkg" to "yt"),
                event = EnforcementTrace.EVALUATE,
                fields = mapOf("pkg" to "yt"),
            ),
        )
        assertTrue(
            EnforcementTraceQuery.shouldCoalesce(
                lastEvent = EnforcementTrace.SESSION_START,
                lastFields = mapOf("pkg" to "yt", "action" to "Continue", "gapMs" to "0"),
                event = EnforcementTrace.SESSION_START,
                fields = mapOf("pkg" to "yt", "action" to "Continue", "gapMs" to "0"),
            ),
        )
        assertFalse(
            EnforcementTraceQuery.shouldCoalesce(
                lastEvent = EnforcementTrace.SESSION_START,
                lastFields = mapOf("pkg" to "yt", "action" to "Continue", "gapMs" to "0"),
                event = EnforcementTrace.SESSION_START,
                fields = mapOf("pkg" to "yt", "action" to "StartFresh", "gapMs" to "61000"),
            ),
        )
    }

    @Test
    fun parseLine_roundTripsFormattedEntry() {
        val entry = EnforcementTrace.Entry(
            epochMs = now,
            event = EnforcementTrace.OVERLAY_SHOW,
            fields = mapOf("pkg" to "yt", "reason" to "sessionLimit", "openGate" to "false"),
        )
        val parsed = EnforcementTraceQuery.parseLine(entry.format())
        assertEquals(entry, parsed)
        assertEquals(null, EnforcementTraceQuery.parseLine(""))
        assertEquals(null, EnforcementTraceQuery.parseLine("not-a-log"))
    }

    @Test
    fun parseFile_filtersBySince() {
        val file = """
            ${now - 10_000_000} evaluate pkg=old
            ${now - 1_000} overlay_show pkg=new
        """.trimIndent()
        val since = EnforcementTraceQuery.sinceEpochMs(EnforcementTraceQuery.Window.LAST_HOUR, now)
        val text = EnforcementTraceQuery.filterFileText(file, since)
        assertFalse(text.contains("old"))
        assertTrue(text.contains("overlay_show"))
    }
}
