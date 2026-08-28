package com.gatekeep.domain

import com.gatekeep.domain.model.ExtensionPolicy

enum class ExtensionDenialReason {
    noLimitTodayDisabled,
    extensionNotAllowed,
    dailyLimitReached,
    tooManyConsecutive,
}

object ExtensionPolicyEvaluator {

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
    ): ExtensionDecision {
        if (isNoLimitTodayRequest) {
            if (!policy.showNoLimitToday) {
                return ExtensionDecision.Denied(ExtensionDenialReason.noLimitTodayDisabled)
            }
            return ExtensionDecision.NoLimitToday
        }

        if (!policy.optionMinutes.contains(requestedMinutes)) {
            return ExtensionDecision.Denied(ExtensionDenialReason.extensionNotAllowed)
        }

        val maxPerDay = policy.maxExtensionsPerDay
        if (maxPerDay != null && maxPerDay > 0 && overridesToday >= maxPerDay) {
            return ExtensionDecision.Denied(ExtensionDenialReason.dailyLimitReached)
        }

        val maxConsecutive = policy.maxConsecutiveExtensions
        val effectiveConsecutiveCap = when {
            maxConsecutive != null && maxPerDay != null -> minOf(maxConsecutive, maxPerDay)
            else -> maxConsecutive
        }
        if (effectiveConsecutiveCap != null && effectiveConsecutiveCap > 0 &&
            consecutiveInSession >= effectiveConsecutiveCap
        ) {
            return ExtensionDecision.Denied(ExtensionDenialReason.tooManyConsecutive)
        }

        return ExtensionDecision.Allowed(requestedMinutes)
    }
}
