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

    data class SeededProfile(
        val profileId: Long,
        val packageName: String,
    )

    data class ProfileSeedConfig(
        val name: String = "Test Profile",
        val dailyLimitMs: Long? = 60 * 60_000L,
        val hourlyLimitMs: Long? = null,
        val weeklyLimitMs: Long? = null,
        val sessionLimitMs: Long? = 15 * 60_000L,
        val onOpenAction: OnOpenAction = OnOpenAction.none,
        val onLimitAction: OnLimitAction = OnLimitAction.hardBlock,
        val onSessionLimitAction: OnSessionLimitAction = OnSessionLimitAction.hardBlock,
        val defaultFrictionMethod: FrictionMethod = FrictionMethod.math,
        val defaultFrictionDifficulty: FrictionDifficulty = FrictionDifficulty.easy,
        val delayOpenSeconds: Int = 0,
        val openWaitDurationSeconds: Int = 3,
        val sessionWaitDurationSeconds: Int = 3,
        val limitWaitDurationSeconds: Int = 3,
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
        val breakDurationMs: Long? = 5 * 60_000L,
        val limitBreakDurationMs: Long? = 5 * 60_000L,
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
                showSessionTimerNotification = true,
                warningAlertsEnabled = true,
            )
        }
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
