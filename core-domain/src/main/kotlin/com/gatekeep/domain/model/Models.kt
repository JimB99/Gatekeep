package com.gatekeep.domain.model

enum class PauseType {
    fiveMin,
    fifteenMin,
    sixtyMin,
    untilDatetime,
    noLimitToday,
    focusMode,
    emergencyBypass,
}

enum class OnOpenAction {
    none,
    pinGate,
    deterrentMath,
    deterrentWait,
}

enum class OnLimitAction {
    notifyOnly,
    limitWithExtensions,
    deterrentMath,
    deterrentWait,
    mandatoryBreak,
    hardBlock,
}

enum class OnSessionLimitAction {
    notifyOnly,
    deterrentMath,
    deterrentWait,
    limitWithExtensions,
    mandatoryBreak,
    hardBlock,
}

data class ExtensionPolicy(
    val optionMinutes: List<Int> = listOf(1, 5, 10),
    val maxExtensionsPerDay: Int? = null,
    val maxConsecutiveExtensions: Int? = null,
    val showNoLimitToday: Boolean = true,
    val customMinutes: Int? = null,
    val customEnabled: Boolean = false,
)

data class ProfileEnforcementConfig(
    val onOpenAction: OnOpenAction = OnOpenAction.none,
    val onLimitAction: OnLimitAction = OnLimitAction.limitWithExtensions,
    val onSessionLimitAction: OnSessionLimitAction = OnSessionLimitAction.limitWithExtensions,
    val deterrentDifficulty: FrictionDifficulty = FrictionDifficulty.medium,
    val openWaitDurationSeconds: Int = 60,
    val sessionWaitDurationSeconds: Int = 60,
    val limitWaitDurationSeconds: Int = 60,
    val sessionBreakDurationMs: Long? = null,
    val limitBreakDurationMs: Long? = null,
    val extensionPolicy: ExtensionPolicy = ExtensionPolicy(),
)

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
    val openWaitDurationSeconds: Int = 60,
    val sessionWaitDurationSeconds: Int = 60,
    val limitWaitDurationSeconds: Int = 60,
    val limitBreakDurationMs: Long? = null,
    val onOpenAction: OnOpenAction = OnOpenAction.none,
    val onLimitAction: OnLimitAction = OnLimitAction.limitWithExtensions,
    val onSessionLimitAction: OnSessionLimitAction = OnSessionLimitAction.limitWithExtensions,
    val extensionPolicy: ExtensionPolicy = ExtensionPolicy(),
) {
    fun enforcementConfig(): ProfileEnforcementConfig = ProfileEnforcementConfig(
        onOpenAction = onOpenAction,
        onLimitAction = onLimitAction,
        onSessionLimitAction = onSessionLimitAction,
        deterrentDifficulty = defaultFrictionDifficulty,
        openWaitDurationSeconds = openWaitDurationSeconds,
        sessionWaitDurationSeconds = sessionWaitDurationSeconds,
        limitWaitDurationSeconds = limitWaitDurationSeconds,
        sessionBreakDurationMs = breakDurationMs,
        limitBreakDurationMs = limitBreakDurationMs,
        extensionPolicy = extensionPolicy,
    )

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
    val excludedMs: Long = 0,
    val frictionStartedAtEpochMs: Long? = null,
    val pendingWaitUntilEpochMs: Long? = null,
    val sessionLimitNotified: Boolean = false,
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
    val enforcementConfig: ProfileEnforcementConfig = profile.enforcementConfig(),
)

sealed class RuleResult {
    data class Allowed(
        val remainingDailyMs: Long?,
        val remainingSessionMs: Long?,
        val remainingHourlyMs: Long?,
        val remainingWeeklyMs: Long?,
        val warningLevel: WarningLevel = WarningLevel.none,
        val notifyLimitReached: Boolean = false,
        val notifyLimitReason: BlockReason? = null,
    ) : RuleResult()

    data class Blocked(
        val reason: BlockReason,
        val breakUntilEpochMs: Long? = null,
        val bypassAllowed: Boolean = true,
        val sessionDeterrent: FrictionMethod? = null,
    ) : RuleResult()

    data class DelayOpen(
        val delaySeconds: Int,
        val openDeterrent: FrictionMethod? = null,
    ) : RuleResult()

    data class OpenDeterrent(
        val method: FrictionMethod,
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
