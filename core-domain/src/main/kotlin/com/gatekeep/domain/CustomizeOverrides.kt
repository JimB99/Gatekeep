package com.gatekeep.domain

import com.gatekeep.domain.model.Profile
import com.gatekeep.domain.model.SchedulePolicyOverrides

/**
 * Customize-mode overrides are self-contained: limits do not inherit from the profile Default tab.
 * 0 = off for a limit tier. Null in storage means "never configured" and triggers pre-fill from profile.
 */
object CustomizeOverrides {

    fun hasAnyLimitValue(overrides: SchedulePolicyOverrides): Boolean =
        overrides.weeklyLimitMs != null ||
            overrides.dailyLimitMs != null ||
            overrides.hourlyLimitMs != null ||
            overrides.sessionLimitMs != null

    fun limitsFromProfile(profile: Profile): SchedulePolicyOverrides = SchedulePolicyOverrides(
        weeklyLimitMs = profile.weeklyLimitMs ?: 0L,
        dailyLimitMs = profile.dailyLimitMs ?: 0L,
        hourlyLimitMs = profile.hourlyLimitMs ?: 0L,
        sessionLimitMs = profile.sessionLimitMs ?: 0L,
    )

    fun fullFromProfile(profile: Profile): SchedulePolicyOverrides = limitsFromProfile(profile).copy(
        onOpenAction = profile.onOpenAction,
        onLimitAction = profile.onLimitAction,
        onSessionLimitAction = profile.onSessionLimitAction,
    )

    /** Editor pre-fill: copy profile defaults when limits were never saved. */
    fun resolveForEditor(profile: Profile, overrides: SchedulePolicyOverrides): SchedulePolicyOverrides {
        val limits = if (!hasAnyLimitValue(overrides)) {
            limitsFromProfile(profile)
        } else {
            SchedulePolicyOverrides(
                weeklyLimitMs = overrides.weeklyLimitMs ?: 0L,
                dailyLimitMs = overrides.dailyLimitMs ?: 0L,
                hourlyLimitMs = overrides.hourlyLimitMs ?: 0L,
                sessionLimitMs = overrides.sessionLimitMs ?: 0L,
            )
        }
        return limits.copy(
            onOpenAction = overrides.onOpenAction ?: profile.onOpenAction,
            onLimitAction = overrides.onLimitAction ?: profile.onLimitAction,
            onSessionLimitAction = overrides.onSessionLimitAction ?: profile.onSessionLimitAction,
        )
    }

    /** Enforcement: customize limits only; 0 or null = off for that tier. */
    fun apply(profile: Profile, overrides: SchedulePolicyOverrides): Profile = profile.copy(
        dailyLimitMs = overrides.dailyLimitMs?.takeIf { it > 0 },
        hourlyLimitMs = overrides.hourlyLimitMs?.takeIf { it > 0 },
        weeklyLimitMs = overrides.weeklyLimitMs?.takeIf { it > 0 },
        sessionLimitMs = overrides.sessionLimitMs?.takeIf { it > 0 },
        onOpenAction = overrides.onOpenAction ?: profile.onOpenAction,
        onLimitAction = overrides.onLimitAction ?: profile.onLimitAction,
        onSessionLimitAction = overrides.onSessionLimitAction ?: profile.onSessionLimitAction,
    )
}
