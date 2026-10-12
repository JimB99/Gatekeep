package com.gatekeep.app.enforcement

/**
 * Time-range export and duplicate-commit coalescing for [EnforcementTrace].
 */
object EnforcementTraceQuery {

    enum class Window(val durationMs: Long?) {
        LAST_15_MIN(15 * 60_000L),
        LAST_HOUR(60 * 60_000L),
        LAST_6_HOURS(6 * 60 * 60_000L),
        LAST_24_HOURS(24 * 60 * 60_000L),
        ALL(null),
    }

    fun sinceEpochMs(window: Window, nowMs: Long): Long? =
        window.durationMs?.let { nowMs - it }

    fun included(epochMs: Long, sinceEpochMs: Long?): Boolean =
        sinceEpochMs == null || epochMs >= sinceEpochMs

    fun filter(
        entries: List<EnforcementTrace.Entry>,
        sinceEpochMs: Long?,
    ): List<EnforcementTrace.Entry> = entries.filter { included(it.epochMs, sinceEpochMs) }

    fun formatExport(
        entries: List<EnforcementTrace.Entry>,
        sinceEpochMs: Long?,
    ): String = filter(entries, sinceEpochMs).joinToString("\n") { it.format() }

    fun filterFileText(fileText: String, sinceEpochMs: Long?): String {
        if (fileText.isBlank()) return ""
        return fileText.lineSequence()
            .filter { line ->
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
        if (lastEvent != event || lastFields != fields) return false
        return event == EnforcementTrace.FOREGROUND_COMMIT ||
            event == EnforcementTrace.OVERLAY_SHOW ||
            event == EnforcementTrace.OVERLAY_SKIP ||
            event == EnforcementTrace.DEVICE_PHASE ||
            event == EnforcementTrace.EVALUATE ||
            event == EnforcementTrace.SESSION_START
    }

    fun parseLine(line: String): EnforcementTrace.Entry? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
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
        return EnforcementTrace.Entry(epoch, event, fields)
    }
}
