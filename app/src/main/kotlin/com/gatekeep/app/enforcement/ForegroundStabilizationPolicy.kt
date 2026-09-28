package com.gatekeep.app.enforcement

/**
 * Reduces false foreground transitions from UsageStats poll noise, launcher blips,
 * and system UI without treating them as leaving the monitored app.
 */
object ForegroundStabilizationPolicy {

    const val DEBOUNCE_MS = 500L
    const val MONITORED_GRACE_MS = 3_000L

    private val transientForegroundPackages = setOf(
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.miui.home",
        "com.sec.android.app.launcher",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.oneplus.launcher",
        "com.teslacoilsw.launcher",
        "com.microsoft.launcher",
    )

    fun isTransientForegroundPackage(packageName: String): Boolean =
        packageName in transientForegroundPackages

    fun isNoiseDestination(packageName: String, ignoredPackages: Set<String>): Boolean =
        packageName in ignoredPackages || isTransientForegroundPackage(packageName)

    /**
     * When the user was just on a monitored app, ignore brief hops to launcher/system UI/IME.
     */
    fun shouldApplyMonitoredGrace(
        incomingPackage: String,
        ignoredPackages: Set<String>,
        lastMonitoredPackage: String?,
        lastMonitoredAtMs: Long,
        nowMs: Long,
    ): Boolean {
        if (lastMonitoredPackage == null) return false
        if (nowMs - lastMonitoredAtMs > MONITORED_GRACE_MS) return false
        return isNoiseDestination(incomingPackage, ignoredPackages)
    }

    fun debounceConfirmed(
        candidatePackage: String,
        candidateFirstSeenMs: Long,
        nowMs: Long,
        debounceMs: Long = DEBOUNCE_MS,
    ): Boolean = nowMs - candidateFirstSeenMs >= debounceMs
}
