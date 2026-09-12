package com.gatekeep.app.enforcement

import com.gatekeep.domain.ProfileMergeEngine
import com.gatekeep.domain.model.UsageSnapshot

object HudUsageDisplay {

    /** Live HUD usage mirrors Current Usage: UsageStats only, summed for shared pools. */
    fun liveSnapshot(
        packageName: String?,
        sharedPool: Boolean,
        monitoredPackages: List<String>,
        statsForPackage: (String) -> UsageSnapshot,
    ): UsageSnapshot? {
        if (packageName == null) return null
        if (!sharedPool) return statsForPackage(packageName)
        val packages = monitoredPackages.ifEmpty { listOf(packageName) }
        return ProfileMergeEngine.sumUsageSnapshots(packages.map(statsForPackage))
    }
}
