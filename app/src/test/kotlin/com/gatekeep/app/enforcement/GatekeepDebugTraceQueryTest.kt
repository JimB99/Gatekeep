package com.gatekeep.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatekeepDebugTraceQueryTest {

    private val now = 1_000_000L

    @Test
    fun filter_lastHour_dropsOlderEvents() {
        val entries = listOf(
            GatekeepDebugTrace.Entry(now - 3_600_000L - 1, "evaluate", mapOf("pkg" to "old")),
            GatekeepDebugTrace.Entry(now - 60_000L, "evaluate", mapOf("pkg" to "recent")),
        )
        val since = GatekeepDebugTraceQuery.sinceEpochMs(GatekeepDebugTraceQuery.Window.LAST_HOUR, now)
        val text = GatekeepDebugTraceQuery.formatExport(entries, since)
        assertFalse(text.contains("old"))
        assertTrue(text.contains("recent"))
    }

    @Test
    fun filter_all_keepsEverything() {
        val entries = listOf(
            GatekeepDebugTrace.Entry(1L, "a", emptyMap()),
            GatekeepDebugTrace.Entry(now, "b", emptyMap()),
        )
        val since = GatekeepDebugTraceQuery.sinceEpochMs(GatekeepDebugTraceQuery.Window.ALL, now)
        assertEquals(2, GatekeepDebugTraceQuery.filter(entries, since).size)
    }

    @Test
    fun coalesce_repeatedForegroundCommit() {
        assertTrue(
            GatekeepDebugTraceQuery.shouldCoalesce(
                lastEvent = GatekeepDebugTrace.FOREGROUND_COMMIT,
                lastFields = mapOf("pkg" to "com.sec.android.app.launcher", "holdMs" to "500"),
                event = GatekeepDebugTrace.FOREGROUND_COMMIT,
                fields = mapOf("pkg" to "com.sec.android.app.launcher", "holdMs" to "500"),
            ),
        )
        assertFalse(
            GatekeepDebugTraceQuery.shouldCoalesce(
                lastEvent = GatekeepDebugTrace.FOREGROUND_COMMIT,
                lastFields = mapOf("pkg" to "com.sec.android.app.launcher", "holdMs" to "500"),
                event = GatekeepDebugTrace.FOREGROUND_COMMIT,
                fields = mapOf("pkg" to "com.whatsapp", "holdMs" to "500"),
            ),
        )
        assertTrue(
            GatekeepDebugTraceQuery.shouldCoalesce(
                lastEvent = GatekeepDebugTrace.OVERLAY_SHOW,
                lastFields = mapOf("pkg" to "yt", "reason" to "sessionLimit", "openGate" to "false"),
                event = GatekeepDebugTrace.OVERLAY_SHOW,
                fields = mapOf("pkg" to "yt", "reason" to "sessionLimit", "openGate" to "false"),
            ),
        )
        assertFalse(
            GatekeepDebugTraceQuery.shouldCoalesce(
                lastEvent = GatekeepDebugTrace.EVALUATE,
                lastFields = mapOf("pkg" to "com.whatsapp"),
                event = GatekeepDebugTrace.FOREGROUND_COMMIT,
                fields = mapOf("pkg" to "com.whatsapp", "holdMs" to "500"),
            ),
        )
        assertTrue(
            GatekeepDebugTraceQuery.shouldCoalesce(
                lastEvent = GatekeepDebugTrace.EVALUATE,
                lastFields = mapOf("pkg" to "yt"),
                event = GatekeepDebugTrace.EVALUATE,
                fields = mapOf("pkg" to "yt"),
            ),
        )
        assertTrue(
            GatekeepDebugTraceQuery.shouldCoalesce(
                lastEvent = GatekeepDebugTrace.SESSION_START,
                lastFields = mapOf("pkg" to "yt", "action" to "Continue", "gapMs" to "0"),
                event = GatekeepDebugTrace.SESSION_START,
                fields = mapOf("pkg" to "yt", "action" to "Continue", "gapMs" to "0"),
            ),
        )
        assertFalse(
            GatekeepDebugTraceQuery.shouldCoalesce(
                lastEvent = GatekeepDebugTrace.SESSION_START,
                lastFields = mapOf("pkg" to "yt", "action" to "Continue", "gapMs" to "0"),
                event = GatekeepDebugTrace.SESSION_START,
                fields = mapOf("pkg" to "yt", "action" to "StartFresh", "gapMs" to "61000"),
            ),
        )
    }

    @Test
    fun parseLine_roundTripsFormattedEntry() {
        val entry = GatekeepDebugTrace.Entry(
            epochMs = now,
            event = GatekeepDebugTrace.OVERLAY_SHOW,
            fields = mapOf("pkg" to "yt", "reason" to "sessionLimit", "openGate" to "false"),
        )
        val parsed = GatekeepDebugTraceQuery.parseLine(entry.format())
        assertEquals(entry, parsed)
        assertEquals(null, GatekeepDebugTraceQuery.parseLine(""))
        assertEquals(null, GatekeepDebugTraceQuery.parseLine("not-a-log"))
    }

    @Test
    fun parseFile_filtersBySince() {
        val file = """
            ${now - 10_000_000} evaluate pkg=old
            ${now - 1_000} overlay_show pkg=new
        """.trimIndent()
        val since = GatekeepDebugTraceQuery.sinceEpochMs(GatekeepDebugTraceQuery.Window.LAST_HOUR, now)
        val text = GatekeepDebugTraceQuery.filterFileText(file, since)
        assertFalse(text.contains("old"))
        assertTrue(text.contains("overlay_show"))
    }
}
