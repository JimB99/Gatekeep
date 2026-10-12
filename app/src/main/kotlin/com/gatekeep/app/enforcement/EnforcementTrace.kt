package com.gatekeep.app.enforcement

import android.content.Context
import android.util.Log
import com.gatekeep.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ring buffer of enforcement decisions for debug logcat, release export, and instrumented tests.
 * Filter: `adb logcat -s GatekeepEnforcement`
 */
@Singleton
class EnforcementTrace @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class Entry(
        val epochMs: Long,
        val event: String,
        val fields: Map<String, String>,
    ) {
        fun format(): String = buildString {
            append(epochMs)
            append(' ')
            append(event)
            if (fields.isNotEmpty()) {
                append(' ')
                append(fields.entries.joinToString(" ") { "${it.key}=${it.value}" })
            }
        }
    }

    private val lock = Any()
    private val buffer = ArrayDeque<Entry>(MAX_ENTRIES)

    init {
        loadFromDisk()
    }

    fun record(event: String, fields: Map<String, String> = emptyMap()) {
        val skipped = synchronized(lock) {
            val last = buffer.lastOrNull()
            if (EnforcementTraceQuery.shouldCoalesce(last?.event, last?.fields, event, fields)) {
                true
            } else {
                val entry = Entry(System.currentTimeMillis(), event, fields)
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, entry.format())
                }
                if (buffer.size >= MAX_ENTRIES) buffer.removeFirst()
                buffer.addLast(entry)
                false
            }
        }
        if (!skipped) persistSnapshot()
    }

    fun snapshot(): List<Entry> = synchronized(lock) { buffer.toList() }

    fun count(event: String): Int = synchronized(lock) { buffer.count { it.event == event } }

    fun clear() {
        synchronized(lock) { buffer.clear() }
        runCatching { persistFile().delete() }
    }

    fun exportText(
        window: EnforcementTraceQuery.Window = EnforcementTraceQuery.Window.ALL,
        nowMs: Long = System.currentTimeMillis(),
    ): String {
        val since = EnforcementTraceQuery.sinceEpochMs(window, nowMs)
        val live = snapshot()
        if (live.isNotEmpty()) {
            return EnforcementTraceQuery.formatExport(live, since)
        }
        val fileText = runCatching { persistFile().takeIf { it.exists() }?.readText().orEmpty() }
            .getOrDefault("")
        return EnforcementTraceQuery.filterFileText(fileText, since)
    }

    private fun loadFromDisk() {
        val text = runCatching { persistFile().takeIf { it.exists() }?.readText().orEmpty() }
            .getOrDefault("")
        if (text.isBlank()) return
        val loaded = text.lineSequence().mapNotNull { EnforcementTraceQuery.parseLine(it) }.toList()
        if (loaded.isEmpty()) return
        synchronized(lock) {
            buffer.clear()
            loaded.takeLast(MAX_ENTRIES).forEach { buffer.addLast(it) }
        }
    }

    private fun persistSnapshot() {
        val lines = synchronized(lock) {
            buffer.toList().takeLast(PERSISTED_ENTRIES).joinToString("\n") { it.format() }
        }
        runCatching { persistFile().writeText(lines) }
    }

    private fun persistFile(): File = File(context.filesDir, FILE_NAME)

    companion object {
        const val TAG = "GatekeepEnforcement"

        const val EVALUATE = "evaluate"
        const val EVALUATE_SKIP_PAUSED = "evaluate_skip_paused"
        const val PENDING_WAIT_RESUME = "pending_wait_resume"
        const val SHOW_OPEN_DETERRENT = "show_open_deterrent"
        const val SHOW_PENDING_SESSION_WAIT = "show_pending_session_wait"
        const val OPEN_GATE_PASSED = "open_gate_passed"
        const val FOREGROUND_COMMIT = "foreground_commit"
        const val SHOW_DELAY_OPEN = "show_delay_open"
        const val DEVICE_PHASE = "device_phase"
        const val SESSION_LEAVE = "session_leave"
        const val SESSION_PAUSE = "session_pause"
        const val SESSION_RESUME = "session_resume"
        const val SESSION_START = "session_start"
        const val OVERLAY_HIDE = "overlay_hide"
        const val OVERLAY_RESTORE = "overlay_restore"
        const val OVERLAY_SHOW = "overlay_show"
        const val OVERLAY_SKIP = "overlay_skip"
        const val HUD_FREEZE = "hud_freeze"
        const val UNLOCK_SKIP = "unlock_skip"
        const val UNLOCK_RESUME = "unlock_resume"

        private const val MAX_ENTRIES = 2_000
        private const val PERSISTED_ENTRIES = 2_000
        private const val FILE_NAME = "enforcement-trace.log"
    }
}
