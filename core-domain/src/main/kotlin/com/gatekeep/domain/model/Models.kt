package com.gatekeep.domain.model

enum class PauseType {
    fiveMin,
    fifteenMin,
    sixtyMin,
    untilDatetime,
    focusMode,
    emergencyBypass,
}

enum class FrictionMethod {
    math,
    waitOneMin,
    holdButton,
    typePhrase,
    password,
}

enum class FrictionDifficulty {
    easy,
    medium,
    hard,
}

enum class UsagePeriod {
    hour,
    day,
    week,
}

enum class AppCategory {
    social,
    games,
    video,
    productivity,
    communication,
    other,
}

data class Profile(
    val id: Long,
    val name: String,
    val isActive: Boolean,
    val passwordHash: String? = null,
    val lockEnabled: Boolean = false,
    val sortOrder: Int = 0,
    val autoScheduleEnabled: Boolean = false,
    val defaultFrictionMethod: FrictionMethod = FrictionMethod.math,
    val defaultFrictionDifficulty: FrictionDifficulty = FrictionDifficulty.medium,
    val delayOpenSeconds: Int = 0,
    val gradualTighteningEnabled: Boolean = false,
    val gradualTighteningTargetDailyMs: Long? = null,
    val gradualTighteningPercentPerWeek: Int = 5,
    val dailyLimitMs: Long? = null,
    val hourlyLimitMs: Long? = null,
    val weeklyLimitMs: Long? = null,
    val sessionLimitMs: Long? = null,
    val breakDurationMs: Long? = null,
    val waitDurationSeconds: Int = 60,
) {
    fun toAppLimit(packageName: String): AppLimit = AppLimit(
        profileId = id,
        packageName = packageName,
        dailyLimitMs = dailyLimitMs,
        hourlyLimitMs = hourlyLimitMs,
        weeklyLimitMs = weeklyLimitMs,
        sessionLimitMs = sessionLimitMs,
        breakDurationMs = breakDurationMs,
        enabled = true,
        frictionMethod = defaultFrictionMethod,
        frictionDifficulty = defaultFrictionDifficulty,
    )
}

data class MonitoredApp(
    val profileId: Long,
    val packageName: String,
    val label: String,
    val category: AppCategory = AppCategory.other,
    val isWhitelistedEssential: Boolean = false,
)

data class AppLimit(
    val profileId: Long,
    val packageName: String,
    val dailyLimitMs: Long? = null,
    val weeklyLimitMs: Long? = null,
    val hourlyLimitMs: Long? = null,
    val sessionLimitMs: Long? = null,
    val breakDurationMs: Long? = null,
    val enabled: Boolean = true,
    val frictionMethod: FrictionMethod? = null,
    val frictionDifficulty: FrictionDifficulty? = null,
    val extensionMsOnBypass: Long = 5 * 60_000L,
)

data class ScheduleWindow(
    val id: Long = 0,
    val profileId: Long,
    val packageName: String? = null,
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
    val isProfileAutoSwitch: Boolean = false,
)

data class Pause(
    val id: Long = 0,
    val profileId: Long?,
    val packageName: String? = null,
    val type: PauseType,
    val untilEpochMs: Long,
)

data class UsageSnapshot(
    val dailyMs: Long = 0,
    val weeklyMs: Long = 0,
    val hourlyMs: Long = 0,
)

data class SessionState(
    val packageName: String,
    val sessionStartEpochMs: Long,
    val breakUntilEpochMs: Long? = null,
)

data class RuleEvaluationContext(
    val nowEpochMs: Long,
    val packageName: String,
    val profile: Profile,
    val limit: AppLimit?,
    val isMonitored: Boolean,
    val usage: UsageSnapshot,
    val sessionState: SessionState?,
    val pauses: List<Pause>,
    val scheduleWindows: List<ScheduleWindow>,
    val focusModeUntilMs: Long? = null,
    val emergencyBypassAvailable: Boolean = false,
    val lastEmergencyBypassEpochMs: Long? = null,
)

sealed class RuleResult {
    data class Allowed(
        val remainingDailyMs: Long?,
        val remainingSessionMs: Long?,
        val remainingHourlyMs: Long?,
        val remainingWeeklyMs: Long?,
        val warningLevel: WarningLevel = WarningLevel.none,
    ) : RuleResult()

    data class Blocked(
        val reason: BlockReason,
        val breakUntilEpochMs: Long? = null,
        val message: String,
    ) : RuleResult()

    data class DelayOpen(
        val delaySeconds: Int,
        val message: String,
    ) : RuleResult()
}

enum class WarningLevel {
    none,
    eightyPercent,
}

enum class BlockReason {
    notMonitored,
    profilePaused,
    appPaused,
    outsideSchedule,
    hourlyLimit,
    dailyLimit,
    weeklyLimit,
    sessionLimit,
    onBreak,
    focusMode,
}

data class MathChallenge(
    val question: String,
    val answer: Int,
    val difficulty: FrictionDifficulty,
)

data class StreakInfo(
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val lastUnderBudgetDay: String?,
)

data class FocusModeState(
    val active: Boolean,
    val untilEpochMs: Long,
    val durationMinutes: Int = 25,
)

data class EmergencyBypassState(
    val available: Boolean,
    val cooldownUntilEpochMs: Long?,
    val extensionMs: Long = 15 * 60_000L,
)
