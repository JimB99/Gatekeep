package com.gatekeep.domain

import com.gatekeep.domain.model.ExtensionPolicy
import com.gatekeep.domain.model.ExtensionSurfaceMode
import com.gatekeep.domain.model.LimitUsageScope

object ExtensionRequestEvaluator {

    fun evaluate(
        policy: ExtensionPolicy,
        source: ExtensionGrantSource,
        requestedMinutes: Int,
        overridesToday: Int,
        consecutiveInSession: Int,
        isNoLimitTodayRequest: Boolean = false,
    ): ExtensionPolicyEvaluator.ExtensionDecision {
        if (!isSurfaceAllowed(policy, source)) {
            return ExtensionPolicyEvaluator.ExtensionDecision.Denied(
                ExtensionDenialReason.extensionNotAllowed,
            )
        }
        if (isNoLimitTodayRequest) {
            return ExtensionPolicyEvaluator.evaluateExtension(
                policy = policy,
                requestedMinutes = 0,
                overridesToday = overridesToday,
                consecutiveInSession = consecutiveInSession,
                isNoLimitTodayRequest = true,
            )
        }
        if (requestedMinutes <= 0) {
            return ExtensionPolicyEvaluator.ExtensionDecision.Denied(
                ExtensionDenialReason.extensionNotAllowed,
            )
        }
        val ownerOverride = source == ExtensionGrantSource.inApp
        return ExtensionPolicyEvaluator.evaluateExtension(
            policy = policy,
            requestedMinutes = requestedMinutes,
            overridesToday = overridesToday,
            consecutiveInSession = consecutiveInSession,
            requireConfiguredMinutes = !ownerOverride,
            enforceQuotas = !ownerOverride,
        )
    }

    fun isSurfaceAllowed(policy: ExtensionPolicy, source: ExtensionGrantSource): Boolean = when (source) {
        ExtensionGrantSource.overlay -> policy.showExtensionsInOverlay
        ExtensionGrantSource.inApp -> policy.allowExtensionsInApp
    }

    fun quotaScope(
        limitUsageScope: LimitUsageScope,
        profileId: Long,
        packageName: String,
    ): ExtensionQuotaScope = when (limitUsageScope) {
        LimitUsageScope.sharedPool -> ExtensionQuotaScope.Profile(profileId)
        LimitUsageScope.perApp -> ExtensionQuotaScope.ProfilePackage(profileId, packageName)
    }
}

sealed class ExtensionQuotaScope {
    data class Profile(val profileId: Long) : ExtensionQuotaScope()
    data class ProfilePackage(val profileId: Long, val packageName: String) : ExtensionQuotaScope()
}
