package com.gatekeep.app.enforcement

import com.gatekeep.domain.model.Profile

/**
 * Aligns session persistence with [EnforcementCoordinator.evaluateInternal], which uses
 * the lowest-id active profile that monitors the foreground package.
 */
object MonitoredPackageProfilePolicy {

    fun primaryProfileId(monitoringProfiles: List<Profile>): Long? =
        monitoringProfiles.minByOrNull { it.id }?.id

    fun primaryProfileId(
        activeProfiles: List<Profile>,
        packageName: String,
        isMonitoredBy: (profileId: Long) -> Boolean,
    ): Long? = primaryProfileId(
        activeProfiles.filter { isMonitoredBy(it.id) },
    )
}
