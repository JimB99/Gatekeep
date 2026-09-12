package com.gatekeep.app.support

import com.gatekeep.app.util.PasswordHasher
import com.gatekeep.data.local.entity.UsageSessionEntity
import com.gatekeep.data.repository.AppSettings
import com.gatekeep.data.repository.ProfileRepository
import com.gatekeep.data.repository.SettingsRepository
import com.gatekeep.data.repository.UsageRepository
import com.gatekeep.domain.TimeBoundaries
import com.gatekeep.domain.UsageSessionRecord
import com.gatekeep.domain.model.AppCategory
import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.ExtensionPolicy
import com.gatekeep.domain.model.ExtensionSurfaceMode
import com.gatekeep.domain.model.FrictionDifficulty
import com.gatekeep.domain.model.FrictionMethod
import com.gatekeep.domain.model.LimitUsageScope
import com.gatekeep.domain.model.MonitoredApp
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.OnSessionLimitAction
import com.gatekeep.domain.model.PauseType
import com.gatekeep.domain.model.Profile
import com.gatekeep.domain.model.SchedulePolicyMode
import com.gatekeep.domain.model.SchedulePolicyOverrides
import com.gatekeep.domain.model.ScheduleSegment
import com.gatekeep.domain.model.ScheduleWindow
import com.gatekeep.domain.model.SessionState
import kotlinx.coroutines.flow.first

object GatekeepTestFixtures {
    const val TEST_PIN = "1234"
    const val PROFILE_PIN = "4321"

    /** Compact durations for instrumented tests — limits are scaled down; only relative values matter. */
    object TestDurations {
        const val DAILY_LIMIT_MS = 60_000L
        const val HOURLY_LIMIT_MS = 30_000L
        const val WEEKLY_LIMIT_MS = 70_000L
        const val SESSION_LIMIT_MS = 3_000L
        const val SESSION_OVER_CAP_MS = SESSION_LIMIT_MS + 500L
        const val BREAK_MS = 1_500L
        const val LIMIT_BREAK_MS = 1_500L
        const val OPEN_WAIT_SEC = 1
        const val SESSION_WAIT_SEC = 2
        const val DELAY_OPEN_SEC = 1
        const val CANCELLED_OPEN_WAIT_SEC = 2
        const val PAUSE_EXPIRY_MS = 800L
        const val FUTURE_OFFSET_MS = 30_000L
        const val EXTENSION_GRACE_MS = 2_000L
        const val EXTENSION_BONUS_MS = 5_000L
        /** Usage above the compact daily cap — exercises grace-cap display without hitting real 24h scale. */
        const val OVER_DAILY_CAP_MS = DAILY_LIMIT_MS + 20_000L
        const val STRICT_PROFILE_LIMIT_MS = 20_000L
        const val LOOSE_PROFILE_LIMIT_MS = 60_000L
        const val STRICT_OVER_CAP_MS = STRICT_PROFILE_LIMIT_MS + 500L
        const val EIGHTY_PERCENT_LIMIT_MS = 10_000L
        const val EIGHTY_PERCENT_USAGE_MS = 8_100L
        const val GRADUAL_TIGHTENING_LIMIT_MS = 30_000L
        const val TIMER_BUFFER_MS = 200L
        const val SESSION_TIMER_ELAPSED_MS = 2_000L

        fun msAfterTimer(seconds: Int): Long = seconds * 1_000L + TIMER_BUFFER_MS
        fun msAfterTimerMs(durationMs: Long): Long = durationMs + TIMER_BUFFER_MS
    }

    data class SeededProfile(
        val profileId: Long,
        val packageName: String,
    )

    data class ProfileSeedConfig(
        val name: String = "Test Profile",
        val dailyLimitMs: Long? = TestDurations.DAILY_LIMIT_MS,
        val hourlyLimitMs: Long? = null,
        val weeklyLimitMs: Long? = null,
        val sessionLimitMs: Long? = TestDurations.SESSION_LIMIT_MS,
        val onOpenAction: OnOpenAction = OnOpenAction.none,
        val onLimitAction: OnLimitAction = OnLimitAction.hardBlock,
        val onSessionLimitAction: OnSessionLimitAction = OnSessionLimitAction.hardBlock,
        val defaultFrictionMethod: FrictionMethod = FrictionMethod.math,
        val defaultFrictionDifficulty: FrictionDifficulty = FrictionDifficulty.easy,
        val delayOpenSeconds: Int = 0,
        val openWaitDurationSeconds: Int = TestDurations.OPEN_WAIT_SEC,
        val sessionWaitDurationSeconds: Int = TestDurations.SESSION_WAIT_SEC,
        val limitWaitDurationSeconds: Int = TestDurations.OPEN_WAIT_SEC,
        val passwordHash: String? = null,
        val lockEnabled: Boolean = false,
        val limitUsageScope: LimitUsageScope = LimitUsageScope.perApp,
        val limitExtensionPolicy: ExtensionPolicy = ExtensionPolicy(
            optionMinutes = listOf(1, 5, 10),
            surfaceMode = ExtensionSurfaceMode.both,
        ),
        val sessionExtensionPolicy: ExtensionPolicy = ExtensionPolicy(
            optionMinutes = listOf(1, 5, 10),
            surfaceMode = ExtensionSurfaceMode.both,
        ),
        val noScheduleMatchMode: SchedulePolicyMode = SchedulePolicyMode.default,
        val noScheduleMatchOverrides: SchedulePolicyOverrides = SchedulePolicyOverrides(),
        val gradualTighteningEnabled: Boolean = false,
        val breakDurationMs: Long? = TestDurations.BREAK_MS,
        val limitBreakDurationMs: Long? = TestDurations.LIMIT_BREAK_MS,
    )

    suspend fun seedAppLockEnabled(
        settingsRepository: SettingsRepository,
        pin: String = TEST_PIN,
        onboardingComplete: Boolean = true,
    ) {
        settingsRepository.updateSettings {
            it.copy(
                onboardingComplete = onboardingComplete,
                appLockEnabled = true,
                appPasswordHash = PasswordHasher.hash(pin),
            )
        }
    }

    suspend fun seedAppLockDisabled(
        settingsRepository: SettingsRepository,
        pin: String = TEST_PIN,
        onboardingComplete: Boolean = true,
    ) {
        settingsRepository.updateSettings {
            it.copy(
                onboardingComplete = onboardingComplete,
                appLockEnabled = false,
                appPasswordHash = PasswordHasher.hash(pin),
            )
        }
    }

    suspend fun seedNoPin(
        settingsRepository: SettingsRepository,
        onboardingComplete: Boolean = true,
    ) {
        settingsRepository.updateSettings {
            it.copy(
                onboardingComplete = onboardingComplete,
                appLockEnabled = true,
                appPasswordHash = null,
            )
        }
    }

    suspend fun seedEnforcementReady(
        settingsRepository: SettingsRepository,
        onboardingComplete: Boolean = true,
        enforcementEnabled: Boolean = true,
    ) {
        settingsRepository.updateSettings {
            it.copy(
                onboardingComplete = onboardingComplete,
                appLockEnabled = false,
                appPasswordHash = null,
                enforcementEnabled = enforcementEnabled,
                accessibilityOptedIn = false,
                focusModeUntilMs = null,
                lastEmergencyBypassEpochMs = null,
                languageTag = "en-GB",
                showSessionTimerNotification = true,
                warningAlertsEnabled = true,
            )
        }
    }

    suspend fun clearAllProfiles(profileRepository: ProfileRepository) {
        profileRepository.observeProfiles().first().forEach { profile ->
            profileRepository.deleteProfile(profile.id)
        }
    }

    suspend fun resetInstrumentedUiState(
        settingsRepository: SettingsRepository,
        profileRepository: ProfileRepository,
    ) {
        clearAllProfiles(profileRepository)
        seedEnforcementReady(settingsRepository)
    }

    suspend fun seedProfileWithMonitoredApp(
        profileRepository: ProfileRepository,
        packageName: String = EnforcementTestPackages.TARGET_A,
        label: String = EnforcementTestPackages.TARGET_A_LABEL,
        config: ProfileSeedConfig = ProfileSeedConfig(),
        activate: Boolean = true,
        extraPackages: List<Pair<String, String>> = emptyList(),
    ): SeededProfile {
        val profileId = profileRepository.createProfile(config.name)
        val profile = Profile(
            id = profileId,
            name = config.name,
            isActive = activate,
            passwordHash = config.passwordHash,
            lockEnabled = config.lockEnabled,
            dailyLimitMs = config.dailyLimitMs,
            hourlyLimitMs = config.hourlyLimitMs,
            weeklyLimitMs = config.weeklyLimitMs,
            sessionLimitMs = config.sessionLimitMs,
            breakDurationMs = config.breakDurationMs,
            limitBreakDurationMs = config.limitBreakDurationMs,
            onOpenAction = config.onOpenAction,
            onLimitAction = config.onLimitAction,
            onSessionLimitAction = config.onSessionLimitAction,
            defaultFrictionMethod = config.defaultFrictionMethod,
            defaultFrictionDifficulty = config.defaultFrictionDifficulty,
            delayOpenSeconds = config.delayOpenSeconds,
            openWaitDurationSeconds = config.openWaitDurationSeconds,
            sessionWaitDurationSeconds = config.sessionWaitDurationSeconds,
            limitWaitDurationSeconds = config.limitWaitDurationSeconds,
            limitExtensionPolicy = config.limitExtensionPolicy,
            sessionExtensionPolicy = config.sessionExtensionPolicy,
            noScheduleMatchMode = config.noScheduleMatchMode,
            noScheduleMatchOverrides = config.noScheduleMatchOverrides,
            limitUsageScope = config.limitUsageScope,
            gradualTighteningEnabled = config.gradualTighteningEnabled,
        )
        profileRepository.updateProfile(profile)
        profileRepository.addMonitoredApp(
            MonitoredApp(profileId, packageName, label, AppCategory.other),
        )
        extraPackages.forEach { (pkg, pkgLabel) ->
            profileRepository.addMonitoredApp(
                MonitoredApp(profileId, pkg, pkgLabel, AppCategory.other),
            )
        }
        profileRepository.upsertLimit(
            AppLimit(
                profileId = profileId,
                packageName = packageName,
                dailyLimitMs = config.dailyLimitMs,
                hourlyLimitMs = config.hourlyLimitMs,
                weeklyLimitMs = config.weeklyLimitMs,
                sessionLimitMs = config.sessionLimitMs,
                breakDurationMs = config.breakDurationMs,
                frictionMethod = config.defaultFrictionMethod,
                frictionDifficulty = config.defaultFrictionDifficulty,
            ),
        )
        if (activate) {
            profileRepository.toggleProfileActive(profileId, true)
        }
        return SeededProfile(profileId, packageName)
    }

    suspend fun seedUsageAtCap(
        usageRepository: UsageRepository,
        profileId: Long,
        packageName: String,
        dailyMs: Long = 0,
        hourlyMs: Long = 0,
        weeklyMs: Long = 0,
        sessionState: SessionState? = null,
    ) {
        val now = System.currentTimeMillis()
        val dayStart = TimeBoundaries.dayStartEpochMs(now)
        val hourStart = TimeBoundaries.hourStartEpochMs(now)
        val weekStart = TimeBoundaries.weekBounds(now).startMs

        if (dailyMs > 0) {
            usageRepository.recordSession(
                packageName,
                profileId,
                dayStart + 1_000,
                dayStart + 1_000 + dailyMs,
            )
        }
        if (hourlyMs > 0) {
            usageRepository.recordSession(
                packageName,
                profileId,
                hourStart + 1_000,
                hourStart + 1_000 + hourlyMs,
            )
        }
        if (weeklyMs > 0) {
            usageRepository.recordSession(
                packageName,
                profileId,
                weekStart + 1_000,
                weekStart + 1_000 + weeklyMs,
            )
        }

        val sessions = usageRepository.getRecentSessions(profileId, 500)
            .map { it.toRecord() }
        if (sessions.isNotEmpty()) {
            usageRepository.aggregateAndStore(sessions)
        }

        sessionState?.let { usageRepository.saveSessionState(it, profileId) }
    }

    suspend fun seedScheduleSegment(
        profileRepository: ProfileRepository,
        profileId: Long,
        mode: SchedulePolicyMode,
        windows: List<ScheduleWindow>,
        label: String = "Test Segment",
        overrides: SchedulePolicyOverrides = SchedulePolicyOverrides(),
        active: Boolean = true,
    ): Long {
        val segmentId = profileRepository.upsertScheduleSegment(
            ScheduleSegment(
                id = 0,
                profileId = profileId,
                label = label,
                isActive = active,
                mode = mode,
                overrides = overrides,
            ),
        )
        windows.forEach { window ->
            profileRepository.addScheduleWindow(
                window.copy(profileId = profileId, segmentId = segmentId),
            )
        }
        return segmentId
    }

    suspend fun seedPause(
        usageRepository: UsageRepository,
        type: PauseType,
        profileId: Long? = null,
        packageName: String? = null,
        untilMs: Long? = null,
    ) {
        usageRepository.addPause(
            type = type,
            nowEpochMs = System.currentTimeMillis(),
            profileId = profileId,
            packageName = packageName,
            untilEpochMs = untilMs,
        )
    }

    suspend fun clearProfileEnforcementData(
        profileRepository: ProfileRepository,
        usageRepository: UsageRepository,
        profileId: Long,
    ) {
        usageRepository.clearSessionStatesForProfile(profileId)
        usageRepository.clearFocusBlocks(listOf(profileId))
        usageRepository.clearAllowPauses(listOf(profileId))
        profileRepository.observeMonitoredApps(profileId).first().forEach { app ->
            usageRepository.clearSessionState(profileId, app.packageName)
        }
    }

    fun lockedSettings(pin: String = TEST_PIN): AppSettings = AppSettings(
        onboardingComplete = true,
        appLockEnabled = true,
        appPasswordHash = PasswordHasher.hash(pin),
    )

    fun lockDisabledWithPin(pin: String = TEST_PIN): AppSettings = AppSettings(
        onboardingComplete = true,
        appLockEnabled = false,
        appPasswordHash = PasswordHasher.hash(pin),
    )

    fun noPinSettings(): AppSettings = AppSettings(
        onboardingComplete = true,
        appLockEnabled = true,
        appPasswordHash = null,
    )

    private fun UsageSessionEntity.toRecord() = UsageSessionRecord(
        packageName = packageName,
        profileId = profileId,
        startEpochMs = startEpochMs,
        endEpochMs = endEpochMs,
    )
}
