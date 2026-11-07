package com.gatekeep.app.enforcement

/**
 * Time-range export and duplicate-commit coalescing for [GatekeepDebugTrace].
 */
object GatekeepDebugTraceQuery {

    enum class Window(val durationMs: Long?) {
        LAST_15_MIN(15 * 60_000L),
        LAST_HOUR(60 * 60_000L),
        LAST_6_HOURS(6 * 60 * 60_000L),
        LAST_24_HOURS(24 * 60 * 60_000L),
        ALL(null),
    }

    /** Default chip selection on Settings → About before copy. */
    val DEFAULT_EXPORT_WINDOW: Window = Window.LAST_15_MIN

    fun sinceEpochMs(window: Window, nowMs: Long): Long? =
        window.durationMs?.let { nowMs - it }

    fun included(epochMs: Long, sinceEpochMs: Long?): Boolean =
        sinceEpochMs == null || epochMs >= sinceEpochMs

    fun filter(
        entries: List<GatekeepDebugTrace.Entry>,
        sinceEpochMs: Long?,
    ): List<GatekeepDebugTrace.Entry> = entries.filter { included(it.epochMs, sinceEpochMs) }

    fun formatExport(
        entries: List<GatekeepDebugTrace.Entry>,
        sinceEpochMs: Long?,
    ): String = filter(entries, sinceEpochMs).joinToString("\n") { it.format() }

    fun filterFileText(fileText: String, sinceEpochMs: Long?): String {
        if (fileText.isBlank()) return ""
        return fileText.lineSequence()
            .filter { line ->
                if (line.startsWith("#")) return@filter false
                val epoch = line.substringBefore(' ').toLongOrNull()
                epoch == null || included(epoch, sinceEpochMs)
            }
            .joinToString("\n")
    }

    fun shouldCoalesce(
        lastEvent: String?,
        lastFields: Map<String, String>?,
        event: String,
        fields: Map<String, String>,
    ): Boolean {
        if (lastEvent != event) return false
        if (event == GatekeepDebugTrace.FOREGROUND_COMMIT) {
            return lastFields?.get("pkg") == fields["pkg"]
        }
        if (lastFields != fields) return false
        return event == GatekeepDebugTrace.OVERLAY_SHOW ||
            event == GatekeepDebugTrace.OVERLAY_SKIP ||
            event == GatekeepDebugTrace.DEVICE_PHASE ||
            event == GatekeepDebugTrace.EVALUATE ||
            event == GatekeepDebugTrace.SESSION_START
    }

    fun mergeCoalescedFields(
        lastFields: Map<String, String>,
        newFields: Map<String, String>,
        event: String,
    ): Map<String, String> {
        if (event != GatekeepDebugTrace.FOREGROUND_COMMIT) return newFields
        val count = (lastFields["count"]?.toLongOrNull() ?: 1L) + 1L
        return newFields + mapOf("count" to count.toString())
    }

    fun normalizeRecordFields(event: String, fields: Map<String, String>): Map<String, String> =
        if (event == GatekeepDebugTrace.FOREGROUND_COMMIT && "count" !in fields) {
            fields + mapOf("count" to "1")
        } else {
            fields
        }

    /**
     * Applies one debug event to an in-memory buffer (coalesce rules included). Returns the entry kept.
     */
    fun appendRecord(
        buffer: ArrayDeque<GatekeepDebugTrace.Entry>,
        event: String,
        fields: Map<String, String>,
        epochMs: Long,
        maxEntries: Int,
    ): GatekeepDebugTrace.Entry {
        val normalized = normalizeRecordFields(event, fields)
        val last = buffer.lastOrNull()
        val entry = if (last != null &&
            shouldCoalesce(last.event, last.fields, event, normalized)
        ) {
            val merged = mergeCoalescedFields(last.fields, normalized, event)
            buffer.removeLast()
            GatekeepDebugTrace.Entry(epochMs, event, merged)
        } else {
            GatekeepDebugTrace.Entry(epochMs, event, normalized)
        }
        if (buffer.size >= maxEntries) buffer.removeFirst()
        buffer.addLast(entry)
        return entry
    }

    fun parseLine(line: String): GatekeepDebugTrace.Entry? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
        val parts = trimmed.split(' ')
        if (parts.size < 2) return null
        val epoch = parts[0].toLongOrNull() ?: return null
        val event = parts[1]
        val fields = linkedMapOf<String, String>()
        for (token in parts.drop(2)) {
            val eq = token.indexOf('=')
            if (eq <= 0) continue
            fields[token.substring(0, eq)] = token.substring(eq + 1)
        }
        return GatekeepDebugTrace.Entry(epoch, event, fields)
    }
}
