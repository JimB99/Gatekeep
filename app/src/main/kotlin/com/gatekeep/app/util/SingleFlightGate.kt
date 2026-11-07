package com.gatekeep.app.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs [block] only when no other [runIfIdle] call is in progress.
 * Overlapping callers return immediately (e.g. periodic refresh ticks while a load runs).
 */
class SingleFlightGate {
    private val mutex = Mutex()

    suspend fun runIfIdle(block: suspend () -> Unit) {
        if (!mutex.tryLock()) return
        try {
            block()
        } finally {
            mutex.unlock()
        }
    }
}
