package com.gatekeep.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatekeepDebugTraceQueryTest {

    private val now = 1_000_000L

    @Test
    fun defaultExportWindow_isLast15Minutes() {
        assertEquals(GatekeepDebugTraceQuery.Window.LAST_15_MIN, GatekeepDebugTraceQuery.DEFAULT_EXPORT_WINDOW)
    }

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
        assertTrue(
            GatekeepDebugTraceQuery.shouldCoalesce(
                lastEvent = GatekeepDebugTrace.FOREGROUND_COMMIT,
                lastFields = mapOf("pkg" to "yt", "holdMs" to "500", "count" to "1"),
                event = GatekeepDebugTrace.FOREGROUND_COMMIT,
                fields = mapOf("pkg" to "yt", "holdMs" to "0"),
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
        assertEquals(
            mapOf("pkg" to "yt", "holdMs" to "0", "count" to "3"),
            GatekeepDebugTraceQuery.mergeCoalescedFields(
                lastFields = mapOf("pkg" to "yt", "holdMs" to "500", "count" to "2"),
                newFields = mapOf("pkg" to "yt", "holdMs" to "0", "count" to "1"),
                event = GatekeepDebugTrace.FOREGROUND_COMMIT,
            ),
        )
        assertEquals(
            mapOf("pkg" to "yt", "holdMs" to "500", "count" to "1"),
            GatekeepDebugTraceQuery.normalizeRecordFields(
                GatekeepDebugTrace.FOREGROUND_COMMIT,
                mapOf("pkg" to "yt", "holdMs" to "500"),
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
    fun appendRecord_coalescesForegroundCommitByPkg() {
        val buffer = ArrayDeque<GatekeepDebugTrace.Entry>()
        GatekeepDebugTraceQuery.appendRecord(
            buffer = buffer,
            event = GatekeepDebugTrace.FOREGROUND_COMMIT,
            fields = mapOf("pkg" to "yt", "holdMs" to "500"),
            epochMs = 100L,
            maxEntries = 10,
        )
        val second = GatekeepDebugTraceQuery.appendRecord(
            buffer = buffer,
            event = GatekeepDebugTrace.FOREGROUND_COMMIT,
            fields = mapOf("pkg" to "yt", "holdMs" to "0"),
            epochMs = 200L,
            maxEntries = 10,
        )
        assertEquals(1, buffer.size)
        assertEquals("2", second.fields["count"])
        assertEquals("0", second.fields["holdMs"])
        assertEquals(200L, second.epochMs)
    }

    @Test
    fun appendRecord_doesNotCoalesceDifferentPackages() {
        val buffer = ArrayDeque<GatekeepDebugTrace.Entry>()
        GatekeepDebugTraceQuery.appendRecord(
            buffer = buffer,
            event = GatekeepDebugTrace.FOREGROUND_COMMIT,
            fields = mapOf("pkg" to "a", "holdMs" to "500"),
            epochMs = 100L,
            maxEntries = 10,
        )
        GatekeepDebugTraceQuery.appendRecord(
            buffer = buffer,
            event = GatekeepDebugTrace.FOREGROUND_COMMIT,
            fields = mapOf("pkg" to "b", "holdMs" to "500"),
            epochMs = 200L,
            maxEntries = 10,
        )
        assertEquals(2, buffer.size)
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
