package com.gatekeep.app.enforcement

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.gatekeep.app.util.EnforcementLog
import com.gatekeep.app.util.UsageStatsCollector
import com.gatekeep.data.repository.ProfileRepository
import com.gatekeep.data.repository.SettingsRepository
import com.gatekeep.data.repository.UsageRepository
import com.gatekeep.domain.ProfileMergeEngine
import com.gatekeep.domain.RuleEngine
import com.gatekeep.domain.SessionTracker
import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.FrictionDifficulty
import com.gatekeep.domain.model.FrictionMethod
import com.gatekeep.domain.model.Profile
import com.gatekeep.domain.model.RuleEvaluationContext
import com.gatekeep.domain.model.RuleResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class EnforcementState(
    val foregroundPackage: String? = null,
    val lastResult: RuleResult? = null,
    val isBlocking: Boolean = false,
    val remainingSessionMs: Long? = null,
    val remainingDailyMs: Long? = null,
    val blockMessage: String? = null,
    val breakUntilMs: Long? = null,
    val appLabel: String? = null,
)

@Singleton
class EnforcementCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileRepository: ProfileRepository,
    private val usageRepository: UsageRepository,
    private val settingsRepository: SettingsRepository,
    private val usageStatsCollector: UsageStatsCollector,
    private val blockOverlay: BlockOverlayManager,
    private val notificationHelper: GatekeepNotificationHelper,
    private val enforcementLog: EnforcementLog,
    private val profileUnlockCache: ProfileUnlockCache,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(EnforcementState())
    val state: StateFlow<EnforcementState> = _state.asStateFlow()

    private var currentForegroundPackage: String? = null
    private var previousForegroundPackage: String? = null
    private var previousSessionStartMs: Long = 0
    private var previousProfileId: Long = 0
    private var sessionStartedForPackage: String? = null
    private var isBlockingActive = false
    private var blockedPackage: String? = null
    private var monitoredPackagesCache: Set<String> = emptySet()
    private var countdownRunnable: Runnable? = null

    private val ignoredForegroundPackages = setOf(
        "com.android.systemui",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.touchtype.swiftkey",
    )

    fun onForegroundAppChanged(packageName: String) {
        if (packageName == currentForegroundPackage) return
        if (packageName == context.packageName) return
        if (isBlockingActive) {
            if (packageName in ignoredForegroundPackages) return
            if (blockedPackage != null && packageName != blockedPackage && packageName !in monitoredPackagesCache) {
                return
            }
        }
        scope.launch {
            try {
                finalizePreviousSession()
                previousForegroundPackage = currentForegroundPackage
                currentForegroundPackage = packageName
                sessionStartedForPackage = null
                evaluate(packageName)
            } catch (e: Exception) {
                enforcementLog.logError("Foreground evaluation failed", e)
            }
        }
    }

    fun refresh() {
        val pkg = currentForegroundPackage ?: usageStatsCollector.getForegroundPackageFallback()
        if (pkg != null) {
            scope.launch {
                try {
                    if (currentForegroundPackage != pkg) {
                        currentForegroundPackage = pkg
                        sessionStartedForPackage = null
                    }
                    evaluate(pkg)
                } catch (e: Exception) {
                    enforcementLog.logError("Refresh failed", e)
                }
            }
        }
    }

    fun grantExtension(packageName: String, extensionMs: Long) {
        scope.launch {
            try {
                isBlockingActive = false
                blockedPackage = null
                blockOverlay.clearFrictionState()
                val profiles = profileRepository.observeActiveProfiles().first()
                val profileId = profiles.firstOrNull()?.id ?: return@launch
                usageRepository.logOverride(packageName, profileId, "friction", extensionMs)
                val session = usageRepository.getSessionState(packageName)
                    ?: SessionTracker.startSession(packageName, System.currentTimeMillis())
                usageRepository.saveSessionState(
                    session.copy(sessionStartEpochMs = System.currentTimeMillis()),
                    profileId,
                )
                sessionStartedForPackage = null
                blockOverlay.hide()
                evaluate(packageName)
            } catch (e: Exception) {
                enforcementLog.logError("Grant extension failed", e)
            }
        }
    }

    fun onBlockDismissed() {
        isBlockingActive = false
        blockedPackage = null
        blockOverlay.clearFrictionState()
    }

    fun startEnforcementService() {
        val intent = Intent(context, EnforcementForegroundService::class.java)
        context.startForegroundService(intent)
    }

    fun pollFallbackForeground() {
        val pkg = usageStatsCollector.getForegroundPackageFallback() ?: return
        onForegroundAppChanged(pkg)
    }

    fun stopCountdownTicker() {
        countdownRunnable?.let { mainHandler.removeCallbacks(it) }
        countdownRunnable = null
        notificationHelper.hideCountdown()
    }

    private suspend fun finalizePreviousSession() {
        val prev = previousForegroundPackage ?: currentForegroundPackage ?: return
        if (previousSessionStartMs > 0 && previousProfileId > 0) {
            val now = System.currentTimeMillis()
            if (now > previousSessionStartMs) {
                usageRepository.recordSession(prev, previousProfileId, previousSessionStartMs, now)
            }
        }
        val oldState = usageRepository.getSessionState(prev)
        if (oldState != null && previousProfileId > 0) {
            usageRepository.saveSessionState(
                SessionTracker.startSession(prev, System.currentTimeMillis())
                    .copy(breakUntilEpochMs = oldState.breakUntilEpochMs),
                previousProfileId,
            )
        }
    }

    private suspend fun evaluate(packageName: String) {
        val settings = settingsRepository.settings.first()
        if (!settings.enforcementEnabled) {
            stopCountdownTicker()
            mainHandler.post { blockOverlay.hide() }
            return
        }

        val activeProfiles = profileRepository.observeActiveProfiles().first()
        if (activeProfiles.isEmpty()) {
            stopCountdownTicker()
            return
        }

        val activeProfileIds = activeProfiles.map { it.id }.toSet()
        val allScheduleWindows = profileRepository.observeAllScheduleWindows().first()
        val pauses = usageRepository.observeActivePauses(System.currentTimeMillis()).first()
        val now = System.currentTimeMillis()

        var isMonitored = false
        var isEssential = false
        var appLabel = packageName
        val limitsForMerge = mutableListOf<AppLimit>()
        val matchingProfiles = mutableListOf<Profile>()

        for (profile in activeProfiles) {
            val apps = profileRepository.observeMonitoredApps(profile.id).first()
            val app = apps.find { it.packageName == packageName } ?: continue
            isMonitored = true
            matchingProfiles.add(profile)
            appLabel = app.label
            if (app.isWhitelistedEssential) isEssential = true
            limitsForMerge.add(profile.toAppLimit(packageName))
        }

        monitoredPackagesCache = buildMonitoredPackageSet(activeProfiles)

        if (!isMonitored) {
            stopCountdownTicker()
            isBlockingActive = false
            blockedPackage = null
            mainHandler.post { blockOverlay.hide() }
            return
        }

        if (isEssential) {
            stopCountdownTicker()
            mainHandler.post { blockOverlay.hide() }
            return
        }

        val primaryProfile = matchingProfiles.first()
        val profileNeedingPin = matchingProfiles.firstOrNull { it.lockEnabled && it.passwordHash != null }
        if (profileNeedingPin != null && !profileUnlockCache.isUnlocked(profileNeedingPin.id, now)) {
            showPinGate(
                packageName = packageName,
                profile = profileNeedingPin,
                message = "Enter profile PIN to open $appLabel",
            )
            return
        }

        val mergedLimit = ProfileMergeEngine.mergedLimitForApp(limitsForMerge, packageName)
        val usage = usageStatsCollector.getUsageSnapshot(packageName, now)

        var sessionState = usageRepository.getSessionState(packageName)
        if (sessionStartedForPackage != packageName) {
            val breakUntil = sessionState?.breakUntilEpochMs
            sessionState = SessionTracker.startSession(packageName, now).copy(breakUntilEpochMs = breakUntil)
            usageRepository.saveSessionState(sessionState, primaryProfile.id)
            sessionStartedForPackage = packageName
        } else if (sessionState == null) {
            sessionState = SessionTracker.startSession(packageName, now)
            usageRepository.saveSessionState(sessionState, primaryProfile.id)
        }
        previousSessionStartMs = sessionState.sessionStartEpochMs
        previousProfileId = primaryProfile.id

        val scheduleAllowed = ProfileMergeEngine.isWithinMergedSchedule(
            windows = allScheduleWindows,
            packageName = packageName,
            profileIds = activeProfileIds,
            nowEpochMs = now,
        )

        if (!scheduleAllowed) {
            showBlocked(
                packageName, "Outside allowed time window", "outsideSchedule", null,
                primaryProfile, mergedLimit,
            )
            return
        }

        val evalContext = RuleEvaluationContext(
            nowEpochMs = now,
            packageName = packageName,
            profile = primaryProfile,
            limit = mergedLimit,
            isMonitored = true,
            usage = usage,
            sessionState = sessionState,
            pauses = pauses,
            scheduleWindows = emptyList(),
            focusModeUntilMs = settings.focusModeUntilMs,
        )

        val result = when (val ruleResult = RuleEngine.evaluate(evalContext)) {
            is RuleResult.Blocked -> ruleResult
            is RuleResult.DelayOpen -> {
                mainHandler.post {
                    blockOverlay.showDelay(ruleResult.delaySeconds, ruleResult.message) {
                        scope.launch { evaluate(packageName) }
                    }
                }
                return
            }
            is RuleResult.Allowed -> ruleResult
        }

        when (result) {
            is RuleResult.Allowed -> {
                isBlockingActive = false
                blockedPackage = null
                mainHandler.post { blockOverlay.hide() }
                if (settings.showSessionTimerNotification) {
                    startCountdownTicker(appLabel, packageName, result.remainingDailyMs, result.remainingSessionMs)
                } else {
                    stopCountdownTicker()
                }
                _state.value = EnforcementState(
                    foregroundPackage = packageName,
                    lastResult = result,
                    remainingSessionMs = result.remainingSessionMs,
                    remainingDailyMs = result.remainingDailyMs,
                    appLabel = appLabel,
                )
            }
            is RuleResult.Blocked -> {
                stopCountdownTicker()
                showBlocked(
                    packageName, result.message, result.reason.name,
                    result.breakUntilEpochMs, primaryProfile, mergedLimit,
                )
            }
            else -> Unit
        }
    }

    private suspend fun buildMonitoredPackageSet(profiles: List<Profile>): Set<String> {
        val set = mutableSetOf<String>()
        profiles.forEach { profile ->
            profileRepository.observeMonitoredApps(profile.id).first().forEach { set.add(it.packageName) }
        }
        return set
    }

    private fun startCountdownTicker(
        appLabel: String,
        packageName: String,
        remainingDailyMs: Long?,
        remainingSessionMs: Long?,
    ) {
        countdownRunnable?.let { mainHandler.removeCallbacks(it) }
        val now = System.currentTimeMillis()
        val sessionDeadline = remainingSessionMs?.let { now + it }
        val dailyDeadline = remainingDailyMs?.let { now + it }
        notificationHelper.showCountdown(appLabel, dailyDeadline, sessionDeadline)

        countdownRunnable = object : Runnable {
            override fun run() {
                scope.launch {
                    val settings = settingsRepository.settings.first()
                    if (!settings.showSessionTimerNotification) return@launch
                    if (currentForegroundPackage != packageName) return@launch
                    evaluate(packageName)
                }
                mainHandler.postDelayed(this, 1000)
            }
        }
        mainHandler.postDelayed(countdownRunnable!!, 1000)
    }

    private fun showPinGate(packageName: String, profile: Profile, message: String) {
        stopCountdownTicker()
        isBlockingActive = true
        blockedPackage = packageName
        mainHandler.post {
            blockOverlay.show(
                packageName = packageName,
                message = message,
                reason = "profilePin",
                breakUntilMs = null,
                frictionMethod = FrictionMethod.password,
                difficulty = profile.defaultFrictionDifficulty,
                extensionMs = 0L,
                profilePasswordHash = profile.passwordHash,
                onProfileUnlocked = { profileUnlockCache.unlock(profile.id) },
            )
        }
    }

    private fun showBlocked(
        packageName: String,
        message: String,
        reason: String,
        breakUntilMs: Long?,
        profile: Profile,
        limit: AppLimit?,
    ) {
        if (blockOverlay.isFrictionInProgress()) return
        isBlockingActive = true
        blockedPackage = packageName
        mainHandler.post {
            val friction = limit?.frictionMethod ?: profile.defaultFrictionMethod
            val difficulty = limit?.frictionDifficulty ?: profile.defaultFrictionDifficulty
            blockOverlay.show(
                packageName = packageName,
                message = message,
                reason = reason,
                breakUntilMs = breakUntilMs,
                frictionMethod = friction,
                difficulty = difficulty,
                extensionMs = limit?.extensionMsOnBypass ?: 5 * 60_000L,
                profilePasswordHash = profile.passwordHash,
                onProfileUnlocked = null,
            )
        }
        _state.value = EnforcementState(
            foregroundPackage = packageName,
            isBlocking = true,
            blockMessage = message,
            breakUntilMs = breakUntilMs,
        )
    }
}
