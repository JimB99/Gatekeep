package com.gatekeep.app.enforcement

import com.gatekeep.data.repository.UsageRepository
import com.gatekeep.domain.ExtensionGrantEngine
import com.gatekeep.domain.ExtensionGrantSource
import com.gatekeep.domain.ExtensionRequestEvaluator
import com.gatekeep.domain.model.BlockReason
import com.gatekeep.domain.model.LimitUsageScope
import com.gatekeep.domain.model.Profile

class ExtensionGrantUseCase(
    private val usageRepository: UsageRepository,
) {
    suspend fun evaluate(
        profile: Profile,
        packageName: String,
        minutes: Int,
        source: ExtensionGrantSource,
        blockedReason: BlockReason?,
        dayStartMs: Long,
        consecutiveInSession: Int,
        isNoLimitToday: Boolean = false,
    ) = ExtensionRequestEvaluator.evaluate(
        policy = ExtensionGrantEngine.policyForReason(
            blockedReason,
            profile.limitExtensionPolicy,
            profile.sessionExtensionPolicy,
        ),
        source = source,
        requestedMinutes = minutes,
        overridesToday = usageRepository.countExtensionOverridesToday(
            profileId = profile.id,
            packageName = packageName,
            dayStartMs = dayStartMs,
            sharedPool = profile.limitUsageScope == LimitUsageScope.sharedPool,
        ),
        consecutiveInSession = consecutiveInSession,
        isNoLimitTodayRequest = isNoLimitToday,
    )
}
