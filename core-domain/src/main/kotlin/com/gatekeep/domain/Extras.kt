package com.gatekeep.domain

import com.gatekeep.domain.model.AppCategory
import com.gatekeep.domain.model.EmergencyBypassState
import com.gatekeep.domain.model.FocusModeState
import com.gatekeep.domain.model.StreakInfo

object AppCategories {
    val categoryPackages: Map<AppCategory, Set<String>> = mapOf(
        AppCategory.social to setOf(
            "com.instagram.android",
            "com.facebook.katana",
            "com.twitter.android",
            "com.zhiliaoapp.musically",
            "com.snapchat.android",
            "com.reddit.frontpage",
        ),
        AppCategory.games to setOf(
            "com.supercell.clashofclans",
            "com.mojang.minecraftpe",
            "com.activision.callofduty.shooter",
        ),
        AppCategory.video to setOf(
            "com.google.android.youtube",
            "com.netflix.mediaclient",
            "tv.twitch.android.app",
        ),
        AppCategory.communication to setOf(
            "com.whatsapp",
            "org.telegram.messenger",
            "com.discord",
            "com.google.android.apps.messaging",
        ),
        AppCategory.productivity to setOf(
            "com.google.android.gm",
            "com.microsoft.office.outlook",
            "com.notion.id",
        ),
    )

    fun categoryForPackage(packageName: String): AppCategory {
        return categoryPackages.entries
            .firstOrNull { (_, packages) -> packageName in packages }
            ?.key
            ?: AppCategory.other
    }

    fun packagesForCategory(category: AppCategory): Set<String> =
        categoryPackages[category] ?: emptySet()
}

object EmergencyBypass {
    private const val COOLDOWN_MS = 24 * 60 * 60_000L
    private const val WEEK_MS = 7 * 24 * 60 * 60_000L

    fun state(nowEpochMs: Long, lastBypassEpochMs: Long?): EmergencyBypassState {
        if (lastBypassEpochMs == null) {
            return EmergencyBypassState(available = true, cooldownUntilEpochMs = null)
        }
        val cooldownUntil = lastBypassEpochMs + COOLDOWN_MS
        val weekElapsed = nowEpochMs - lastBypassEpochMs >= WEEK_MS
        val cooldownDone = nowEpochMs >= cooldownUntil
        return EmergencyBypassState(
            available = weekElapsed || cooldownDone,
            cooldownUntilEpochMs = if (cooldownDone) null else cooldownUntil,
        )
    }
}

object FocusMode {
    const val DEFAULT_MINUTES = 25

    fun activate(nowEpochMs: Long, durationMinutes: Int = DEFAULT_MINUTES): FocusModeState =
        FocusModeState(
            active = true,
            untilEpochMs = nowEpochMs + durationMinutes * 60_000L,
            durationMinutes = durationMinutes,
        )

    fun isActive(state: FocusModeState?, nowEpochMs: Long): Boolean =
        state?.active == true && nowEpochMs < state.untilEpochMs
}

object GradualTightening {
    fun effectiveDailyLimit(
        baseLimitMs: Long,
        targetLimitMs: Long,
        weeksElapsed: Int,
        percentPerWeek: Int,
    ): Long {
        if (weeksElapsed <= 0) return baseLimitMs
        val reduction = baseLimitMs * (percentPerWeek / 100.0) * weeksElapsed
        return (baseLimitMs - reduction).toLong().coerceAtLeast(targetLimitMs)
    }
}

object StreakCalculator {
    fun calculate(dailyUnderBudget: List<Boolean>): StreakInfo {
        if (dailyUnderBudget.isEmpty()) {
            return StreakInfo(0, 0, null)
        }
        var current = 0
        var longest = 0
        var temp = 0
        for (under in dailyUnderBudget) {
            if (under) {
                temp++
                longest = maxOf(longest, temp)
            } else {
                temp = 0
            }
        }
        for (under in dailyUnderBudget.reversed()) {
            if (under) current++ else break
        }
        return StreakInfo(current, longest, null)
    }
}
