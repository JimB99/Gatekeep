package com.gatekeep.app.enforcement

import android.util.Log
import com.gatekeep.app.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ring buffer of enforcement decisions for debug builds, logcat, and instrumented tests.
 * Filter: `adb logcat -s GatekeepEnforcement`
 */
@Singleton
class EnforcementTrace @Inject constructor() {

    data class Entry(
        val epochMs: Long,
        val event: String,
        val fields: Map<String, String>,
    ) {
        fun format(): String = buildString {
            append(event)
            if (fields.isNotEmpty()) {
                append(' ')
                append(fields.entries.joinToString(" ") { "${it.key}=${it.value}" })
            }
        }
    }

    private val lock = Any()
    private val buffer = ArrayDeque<Entry>(MAX_ENTRIES)

    fun record(event: String, fields: Map<String, String> = emptyMap()) {
        val entry = Entry(System.currentTimeMillis(), event, fields)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, entry.format())
        }
        synchronized(lock) {
            if (buffer.size >= MAX_ENTRIES) buffer.removeFirst()
            buffer.addLast(entry)
        }
    }

    fun snapshot(): List<Entry> = synchronized(lock) { buffer.toList() }

    fun count(event: String): Int = synchronized(lock) { buffer.count { it.event == event } }

    fun clear() = synchronized(lock) { buffer.clear() }

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

        private const val MAX_ENTRIES = 400
    }
}
