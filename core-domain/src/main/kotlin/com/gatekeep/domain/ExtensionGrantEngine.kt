package com.gatekeep.domain

import com.gatekeep.domain.model.BlockReason
import com.gatekeep.domain.model.LimitUsageScope

enum class ExtensionGrantSource {
    overlay,
    inApp,
}

object ExtensionGrantEngine {

    data class GrantPlan(
        val profileId: Long,
        val packageName: String,
        val extensionMs: Long,
        val sessionExcludedMsDelta: Long,
        val graceProfileId: Long?,
        val gracePackageName: String?,
        val graceUntilEpochMs: Long?,
        val blockedReason: BlockReason?,
    )

    fun planGrant(
        profileId: Long,
        packageName: String,
        minutes: Int,
        nowEpochMs: Long,
        limitUsageScope: LimitUsageScope,
        blockedReason: BlockReason?,
        source: ExtensionGrantSource,
    ): GrantPlan {
        val extensionMs = minutes.toLong() * 60_000L
        val graceUntil = nowEpochMs + extensionMs
        val isSessionBlock = blockedReason in setOf(
            BlockReason.sessionLimit,
            BlockReason.onBreak,
        )
        val isPeriodBlock = blockedReason in setOf(
            BlockReason.dailyLimit,
            BlockReason.hourlyLimit,
            BlockReason.weeklyLimit,
        )

        val sessionDelta = when {
            isSessionBlock -> extensionMs
            source == ExtensionGrantSource.inApp -> extensionMs
            else -> 0L
        }

        val graceProfileId: Long?
        val gracePackageName: String?
        val graceUntilEpochMs: Long?

        when {
            isSessionBlock && source == ExtensionGrantSource.overlay -> {
                graceProfileId = null
                gracePackageName = null
                graceUntilEpochMs = null
            }
            isPeriodBlock || (source == ExtensionGrantSource.inApp && blockedReason == null) -> {
                if (limitUsageScope == LimitUsageScope.sharedPool) {
                    graceProfileId = profileId
                    gracePackageName = null
                } else {
                    graceProfileId = profileId
                    gracePackageName = packageName
                }
                graceUntilEpochMs = graceUntil
            }
            source == ExtensionGrantSource.inApp -> {
                if (limitUsageScope == LimitUsageScope.sharedPool) {
                    graceProfileId = profileId
                    gracePackageName = null
                } else {
                    graceProfileId = profileId
                    gracePackageName = packageName
                }
                graceUntilEpochMs = graceUntil
            }
            else -> {
                graceProfileId = null
                gracePackageName = null
                graceUntilEpochMs = null
            }
        }

        return GrantPlan(
            profileId = profileId,
            packageName = packageName,
            extensionMs = extensionMs,
            sessionExcludedMsDelta = sessionDelta,
            graceProfileId = graceProfileId,
            gracePackageName = gracePackageName,
            graceUntilEpochMs = graceUntilEpochMs,
            blockedReason = blockedReason,
        )
    }

    fun policyForReason(
        blockedReason: BlockReason?,
        limitExtensionPolicy: com.gatekeep.domain.model.ExtensionPolicy,
        sessionExtensionPolicy: com.gatekeep.domain.model.ExtensionPolicy,
    ): com.gatekeep.domain.model.ExtensionPolicy = when (blockedReason) {
        BlockReason.sessionLimit, BlockReason.onBreak -> sessionExtensionPolicy
        else -> limitExtensionPolicy
    }
}
