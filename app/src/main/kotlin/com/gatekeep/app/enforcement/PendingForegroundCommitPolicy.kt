package com.gatekeep.app.enforcement

/**
 * Schedules accessibility foreground commits without debounce starvation when holdMs
 * flickers or stale in-memory open-gate pass lengthens holds before evaluate runs.
 */
object PendingForegroundCommitPolicy {

    const val MONITORED_MAX_PENDING_MS = 1_500L

    fun effectiveHoldMs(
        routeHoldMs: Long,
        packageName: String,
        monitoredPackages: Set<String>,
        currentForegroundPackage: String?,
        openGatePassedPackage: String?,
    ): Long {
        if (packageName !in monitoredPackages) return routeHoldMs
        val openingFromElsewhere =
            currentForegroundPackage == null ||
                currentForegroundPackage != packageName ||
                ForegroundStabilizationPolicy.isTransientForegroundPackage(currentForegroundPackage)
        if (openingFromElsewhere) return 0L
        if (openGatePassedPackage != packageName) return 0L
        return routeHoldMs
    }

    fun rescheduleHoldMs(pendingHoldMs: Long, newHoldMs: Long): Long =
        minOf(pendingHoldMs, newHoldMs)

    /**
     * In-memory open-gate pass must match the live sitting for debounce; evaluate still uses Room.
     */
    fun shouldClearStaleInMemoryOpenGatePass(
        candidatePackage: String,
        openGatePassedPackage: String?,
        sessionStartedForPackage: String?,
    ): Boolean =
        openGatePassedPackage == candidatePackage && sessionStartedForPackage != candidatePackage

    fun debounceConfirmed(
        candidateFirstSeenMs: Long,
        nowMs: Long,
        debounceMs: Long,
    ): Boolean = nowMs - candidateFirstSeenMs >= debounceMs

    fun forceCommitNow(
        candidatePackage: String,
        monitoredPackages: Set<String>,
        candidateFirstSeenMs: Long,
        nowMs: Long,
    ): Boolean {
        if (candidatePackage !in monitoredPackages) return false
        return nowMs - candidateFirstSeenMs >= MONITORED_MAX_PENDING_MS
    }
}
