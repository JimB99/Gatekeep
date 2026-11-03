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
        val applyPeriodAppUse: Boolean,
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
        val applyPeriodAppUse = isPeriodBlock ||
            (source == ExtensionGrantSource.inApp && blockedReason == null)

        // Extension grace (see stackedGraceUntilEpochMs): optional short post-grant pause so
        // enforcement does not re-block the instant the overlay closes while usage is still
        // above the old cap. User-facing +N minutes on period limits comes from explicit
        // display cap + remaining (ExtensionDisplayLogic), not from this pause.
        // Intentionally not wired here yet — set graceProfileId/graceUntilEpochMs when adding
        // a measured anti-flash window (cap grace at explicit HUD line if enabled).
        return GrantPlan(
            profileId = profileId,
            packageName = packageName,
            extensionMs = extensionMs,
            sessionExcludedMsDelta = sessionDelta,
            graceProfileId = null,
            gracePackageName = null,
            graceUntilEpochMs = null,
            blockedReason = blockedReason,
            applyPeriodAppUse = applyPeriodAppUse,
        )
    }

    /** End time for a stacked extensionGrace pause (now + extensionMs, extended if grace already active). */
    fun stackedGraceUntilEpochMs(
        nowEpochMs: Long,
        extensionMs: Long,
        existingGraceUntilEpochMs: Long?,
    ): Long {
        val base = maxOf(existingGraceUntilEpochMs ?: nowEpochMs, nowEpochMs)
        return base + extensionMs
    }

    /** Active extensionGrace pause for profile/package, if any (feeds RuleEngine → LimitEvaluator grace path). */
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
