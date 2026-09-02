package com.gatekeep.domain

import com.gatekeep.domain.model.BlockReason
import com.gatekeep.domain.model.LimitUsageScope
import com.gatekeep.domain.model.Pause
import com.gatekeep.domain.model.PauseType

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
        existingGraceUntilEpochMs: Long? = null,
    ): GrantPlan {
        val extensionMs = minutes.toLong() * 60_000L
        val graceUntil = stackedGraceUntilEpochMs(
            nowEpochMs = nowEpochMs,
            extensionMs = extensionMs,
            existingGraceUntilEpochMs = existingGraceUntilEpochMs,
        )
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

    fun stackedGraceUntilEpochMs(
        nowEpochMs: Long,
        extensionMs: Long,
        existingGraceUntilEpochMs: Long?,
    ): Long {
        val base = maxOf(existingGraceUntilEpochMs ?: nowEpochMs, nowEpochMs)
        return base + extensionMs
    }

    fun activeGraceUntilEpochMs(
        pauses: List<Pause>,
        profileId: Long,
        packageName: String,
        nowEpochMs: Long,
        sharedPool: Boolean,
    ): Long? {
        val expectedPackage = if (sharedPool) null else packageName
        return pauses
            .filter {
                it.type == PauseType.extensionGrace &&
                    it.untilEpochMs > nowEpochMs &&
                    it.profileId == profileId &&
                    it.packageName == expectedPackage
            }
            .maxOfOrNull { it.untilEpochMs }
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
