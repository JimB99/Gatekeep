package com.gatekeep.app.enforcement

import com.gatekeep.domain.ProfileMergeEngine
import com.gatekeep.domain.UsageSnapshotResolver
import com.gatekeep.domain.model.UsageSnapshot

object HudUsageDisplay {

    /** Stats snapshot for HUD, summed for shared pools (callers merge with persisted before display). */
    fun liveStatsSnapshot(
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

    fun mergeWithPersisted(
        stats: UsageSnapshot?,
        persisted: UsageSnapshot?,
    ): UsageSnapshot? {
        if (stats == null && persisted == null) return null
        return UsageSnapshotResolver.merge(
            stats = stats ?: UsageSnapshot(),
            persisted = persisted ?: UsageSnapshot(),
        )
    }

    fun liveSnapshot(
        packageName: String?,
        sharedPool: Boolean,
        monitoredPackages: List<String>,
        statsForPackage: (String) -> UsageSnapshot,
    ): UsageSnapshot? = liveStatsSnapshot(packageName, sharedPool, monitoredPackages, statsForPackage)
}
