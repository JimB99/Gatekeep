package com.gatekeep.domain

import com.gatekeep.domain.model.ExtensionPolicy

enum class ExtensionDenialReason {
    noLimitTodayDisabled,
    extensionNotAllowed,
    dailyLimitReached,
    tooManyConsecutive,
}

object ExtensionPolicyEvaluator {

    fun effectiveConsecutiveCap(policy: ExtensionPolicy): Int? {
        val maxPerDay = policy.maxExtensionsPerDay
        val maxConsecutive = policy.maxConsecutiveExtensions
        return when {
            maxConsecutive != null && maxPerDay != null -> minOf(maxConsecutive, maxPerDay)
            maxConsecutive != null -> maxConsecutive
            maxPerDay != null -> maxPerDay
            else -> null
        }
    }

    sealed class ExtensionDecision {
        data class Allowed(val minutes: Int) : ExtensionDecision()
        data object NoLimitToday : ExtensionDecision()
        data class Denied(val reason: ExtensionDenialReason) : ExtensionDecision()
    }

    fun evaluateExtension(
        policy: ExtensionPolicy,
        requestedMinutes: Int,
        overridesToday: Int,
        consecutiveInSession: Int,
        isNoLimitTodayRequest: Boolean = false,
        requireConfiguredMinutes: Boolean = true,
        enforceQuotas: Boolean = true,
    ): ExtensionDecision {
        if (isNoLimitTodayRequest) {
            if (!policy.showNoLimitToday) {
                return ExtensionDecision.Denied(ExtensionDenialReason.noLimitTodayDisabled)
            }
            return ExtensionDecision.NoLimitToday
        }

        if (requireConfiguredMinutes && !policy.optionMinutes.contains(requestedMinutes)) {
            return ExtensionDecision.Denied(ExtensionDenialReason.extensionNotAllowed)
        }

        if (enforceQuotas) {
            val maxPerDay = policy.maxExtensionsPerDay
            if (maxPerDay != null && maxPerDay > 0 && overridesToday >= maxPerDay) {
                return ExtensionDecision.Denied(ExtensionDenialReason.dailyLimitReached)
            }

            val effectiveConsecutiveCap = effectiveConsecutiveCap(policy)
            if (effectiveConsecutiveCap != null && effectiveConsecutiveCap > 0 &&
                consecutiveInSession >= effectiveConsecutiveCap
            ) {
                return ExtensionDecision.Denied(ExtensionDenialReason.tooManyConsecutive)
            }
        }

        return ExtensionDecision.Allowed(requestedMinutes)
    }
}
