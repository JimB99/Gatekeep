package com.gatekeep.app.enforcement

import android.content.Context
import android.os.Build
import android.util.Log
import com.gatekeep.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ring buffer of app debug events for logcat, user export (About), and instrumented tests.
 * Filter: `adb logcat -s GatekeepDebug`
 */
@Singleton
class GatekeepDebugTrace @Inject constructor(
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
            if (GatekeepDebugTraceQuery.shouldCoalesce(last?.event, last?.fields, event, fields)) {
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
        window: GatekeepDebugTraceQuery.Window = GatekeepDebugTraceQuery.Window.ALL,
        nowMs: Long = System.currentTimeMillis(),
    ): String {
        val since = GatekeepDebugTraceQuery.sinceEpochMs(window, nowMs)
        val header = exportHeader(window, nowMs)
        val live = snapshot()
        val body = if (live.isNotEmpty()) {
            GatekeepDebugTraceQuery.formatExport(live, since)
        } else {
            val fileText = runCatching { persistFile().takeIf { it.exists() }?.readText().orEmpty() }
                .getOrDefault("")
            GatekeepDebugTraceQuery.filterFileText(fileText, since)
        }
        return if (body.isBlank()) "" else "$header\n$body"
    }

    private fun exportHeader(window: GatekeepDebugTraceQuery.Window, nowMs: Long): String =
        buildString {
            append("# Gatekeep debug log")
            append("\n# version=")
            append(BuildConfig.VERSION_NAME)
            append(" (")
            append(BuildConfig.VERSION_CODE)
            append(")")
            append("\n# window=")
            append(window.name)
            append("\n# exportedAtMs=")
            append(nowMs)
            append("\n# device=")
            append(Build.MANUFACTURER)
            append(' ')
            append(Build.MODEL)
        }

    private fun loadFromDisk() {
        val text = runCatching {
            persistFile().takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
                ?: legacyPersistFile().takeIf { it.exists() }?.readText().orEmpty()
        }.getOrDefault("")
        if (text.isBlank()) return
        val loaded = text.lineSequence()
            .mapNotNull { GatekeepDebugTraceQuery.parseLine(it) }
            .toList()
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

    private fun legacyPersistFile(): File = File(context.filesDir, LEGACY_FILE_NAME)

    companion object {
        const val TAG = "GatekeepDebug"

        const val EVALUATE = "evaluate"
        const val EVALUATE_SKIP_PAUSED = "evaluate_skip_paused"
        const val PENDING_WAIT_RESUME = "pending_wait_resume"
        const val SHOW_OPEN_DETERRENT = "show_open_deterrent"
        const val SHOW_PENDING_SESSION_WAIT = "show_pending_session_wait"
        const val OPEN_GATE_PASSED = "open_gate_passed"
        const val FOREGROUND_COMMIT = "foreground_commit"
        const val FOREGROUND_COMMIT_SUPPRESSED = "foreground_commit_suppressed"
        const val OPEN_GATEKEEP_FROM_OVERLAY = "open_gatekeep_from_overlay"
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

        const val EXTENSION_GRANT = "extension_grant"
        const val EXTENSION_DENIED = "extension_denied"
        const val EXTENSION_RESET = "extension_reset"
        const val APP_ERROR = "app_error"
        const val PERMISSION_BANNER_SHOWN = "permission_banner_shown"
        const val PROFILE_SAVE = "profile_save"

        private const val MAX_ENTRIES = 2_000
        private const val PERSISTED_ENTRIES = 2_000
        private const val FILE_NAME = "gatekeep-debug.log"
        private const val LEGACY_FILE_NAME = "enforcement-trace.log"
    }
}
