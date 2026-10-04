package com.gatekeep.app.enforcement

/**
 * Decides when to re-run [com.gatekeep.app.enforcement.EnforcementCoordinator.evaluate]
 * after the device wakes without a foreground package change.
 *
 * Keyguard unlock often leaves the same [currentForegroundPackage], so accessibility
 * never commits a new foreground event and open gate / session HUD never start (RES-01).
 */
object MonitoredForegroundResumePolicy {

    /** How long [lastMonitoredForegroundPackage] still counts as the effective resume target. */
    const val DEFAULT_LAST_MONITORED_WINDOW_MS = 120_000L

    data class Input(
        val targetPackage: String?,
        val monitoredPackages: Set<String>,
        val lastMonitoredPackage: String?,
        val lastMonitoredAtMs: Long,
        val nowMs: Long,
        val lastMonitoredWindowMs: Long = DEFAULT_LAST_MONITORED_WINDOW_MS,
    )

    /**
     * @return true when screen-on should trigger evaluate(sync) for [Input.targetPackage].
     */
    fun shouldForceEvaluateOnScreenResume(input: Input): Boolean {
        val pkg = input.targetPackage ?: return false
        if (!isMonitoredResumeTarget(pkg, input)) return false
        // Re-run rules after keyguard: no foreground transition fires for the same activity.
        return true
    }

    private fun isMonitoredResumeTarget(pkg: String, input: Input): Boolean {
        if (pkg in input.monitoredPackages) return true
        if (input.lastMonitoredPackage != pkg) return false
        val age = input.nowMs - input.lastMonitoredAtMs
        return age in 0..input.lastMonitoredWindowMs
    }
}
