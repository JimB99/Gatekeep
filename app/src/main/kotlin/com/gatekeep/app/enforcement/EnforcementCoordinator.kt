package com.gatekeep.app.enforcement

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.gatekeep.app.util.BlockMessageResolver
import com.gatekeep.app.util.EnforcementLog
import com.gatekeep.app.util.UsageStatsCollector
import com.gatekeep.app.util.withAppLocale
import com.gatekeep.data.repository.ProfileRepository
import com.gatekeep.data.repository.SettingsRepository
import com.gatekeep.data.repository.UsageRepository
import com.gatekeep.domain.EnforcementPollInterval
import com.gatekeep.domain.ExtensionPolicyEvaluator
import com.gatekeep.domain.ProfileMergeEngine
import com.gatekeep.domain.RuleEngine
import com.gatekeep.domain.SessionTracker
import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.BlockReason
import com.gatekeep.domain.model.FrictionDifficulty
import com.gatekeep.domain.model.FrictionMethod
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.Profile
import com.gatekeep.domain.model.RuleEvaluationContext
import com.gatekeep.domain.model.RuleResult
import com.gatekeep.domain.model.SessionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val screenStateMonitor: ScreenStateMonitor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(EnforcementState())
    val state: StateFlow<EnforcementState> = _state.asStateFlow()

    init {
        screenStateMonitor.register { offMs ->
            scope.launch { applyScreenOffExclusion(offMs) }
        }
    }

    private var currentForegroundPackage: String? = null
    private var previousForegroundPackage: String? = null
    private var previousSessionStartMs: Long = 0
    private var previousProfileId: Long = 0
    private var sessionStartedForPackage: String? = null
    private var blockPresentationState = BlockPresentationState()
    private var monitoredPackagesCache: Set<String> = emptySet()
    private var countdownRunnable: Runnable? = null
    private var lastMonitoredForegroundPackage: String? = null
    private var lastMonitoredForegroundAtMs: Long = 0
    private var sessionDeadlineMs: Long? = null
    private var dailyDeadlineMs: Long? = null
    private var hourlyDeadlineMs: Long? = null
    private var weeklyDeadlineMs: Long? = null
    private var countdownDailyLimitMs: Long? = null
    private var countdownHourlyLimitMs: Long? = null
    private var countdownWeeklyLimitMs: Long? = null
    private var countdownUsedTodayMs: Long? = null
    private var countdownAppLabel: String? = null
    private var countdownPackageName: String? = null
    private var enforcementLoopRunnable: Runnable? = null
    private var showCountdownNotification = false
    private var consecutiveExtensionCount = 0
    private var openGatePassedPackage: String? = null
    private val warnedPackagesToday = mutableSetOf<String>()
    private var warnedDayStartMs: Long = 0L
    private var lastNotificationBody: String? = null
    private var countdownNotificationTitle: String? = null
    private val notifiedLimitKeys = mutableSetOf<String>()
    private val evaluateMutex = Mutex()
    private var blockEnteredAtMs: Long = 0L
    private val lastForegroundEvaluationAtMs = mutableMapOf<String, Long>()
    private var foregroundPollRunnable: Runnable? = null

    private val isBlockingActive: Boolean
        get() = BlockPresentationReducer.isBlockingActive(blockPresentationState)

    private val blockedPackage: String?
        get() = BlockPresentationReducer.blockedPackage(blockPresentationState)

    private val blockGeneration: Long
        get() = blockPresentationState.generation

    private val localizedContext: Context
        get() = context.withAppLocale()

    private val ignoredForegroundPackages = setOf(
        "com.android.systemui",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.touchtype.swiftkey",
    )

    fun onForegroundAppChanged(packageName: String, windowClassName: String? = null) {
        if (packageName == currentForegroundPackage && !shouldReevaluateForeground(packageName)) return

        if (packageName == context.packageName) {
            if (BlockPresentationReducer.shouldIgnoreGatekeepForegroundEvent(
                    blockPresentationState,
                    blockOverlay.isVisible(),
                    windowClassName,
                )
            ) {
                return
            }
            currentForegroundPackage = packageName
            onGatekeepActivityForegrounded()
            return
        }

        if (packageName in ignoredForegroundPackages) {
            if (lastMonitoredForegroundPackage != null &&
                System.currentTimeMillis() - lastMonitoredForegroundAtMs < 3_000
            ) {
                reconcileForegroundFromUsageStats()
                return
            }
            if (isBlockingActive) return
            reconcileForegroundFromUsageStats()
            return
        }

        if (isBlockingActive && blockedPackage != null && packageName != blockedPackage) {
            if (System.currentTimeMillis() - blockEnteredAtMs < BLOCK_STABILIZATION_MS) {
                return
            }
        }

        if (shouldReevaluateForeground(packageName) && packageName == currentForegroundPackage) {
            sessionStartedForPackage = null
        }

        val prevPackage = currentForegroundPackage
        currentForegroundPackage = packageName

        scope.launch {
            try {
                handleForegroundChange(packageName, prevPackage)
            } catch (e: Exception) {
                enforcementLog.logError("Foreground evaluation failed", e)
            }
        }
    }

    private suspend fun handleForegroundChange(packageName: String, prevPackage: String?) {
        finalizePreviousSession()
        previousForegroundPackage = prevPackage

        if (prevPackage != null &&
            prevPackage in monitoredPackagesCache &&
            prevPackage != packageName &&
            sessionStartedForPackage == prevPackage
        ) {
            sessionStartedForPackage = null
        }

        if (packageName != blockedPackage) {
            consecutiveExtensionCount = 0
        }
        if (packageName != openGatePassedPackage) {
            openGatePassedPackage = null
        }

        if (isBlockingActive && blockedPackage != null && packageName != blockedPackage) {
            if (monitoredPackagesCache.isNotEmpty() && packageName !in monitoredPackagesCache) {
                transitionToHiddenForOtherApp()
                return
            }
        }

        restoreVisibleBlockIfNeeded(packageName)
        evaluate(packageName)
    }

    private fun transitionToHiddenForOtherApp() {
        if (blockPresentationState.presentation is BlockPresentation.Visible) {
            blockPresentationState = BlockPresentationReducer.onHideForOtherApp(blockPresentationState)
            mainHandler.post { blockOverlay.hideTemporarily() }
        }
    }

    private fun restoreVisibleBlockIfNeeded(packageName: String) {
        if (blockPresentationState.presentation is BlockPresentation.HiddenForOtherApp &&
            blockedPackage == packageName
        ) {
            blockPresentationState = BlockPresentationReducer.onReturnToBlockedApp(
                blockPresentationState,
                packageName,
            )
        }
    }

    private fun syncOverlayVisibility() {
        val fg = currentForegroundPackage
        val blocked = blockedPackage
        if (isBlockingActive && blocked != null && fg == blocked) {
            restoreVisibleBlockIfNeeded(blocked)
            return
        }
        if (isBlockingActive && blocked != null && fg != blocked) {
            transitionToHiddenForOtherApp()
        }
    }

    private fun enterBlockState(packageName: String) {
        blockPresentationState = BlockPresentationReducer.onBlockEntered(blockPresentationState, packageName)
        blockEnteredAtMs = System.currentTimeMillis()
        stopCountdownTicker()
    }

    private fun clearBlockState() {
        blockPresentationState = BlockPresentationReducer.onBlockCleared(blockPresentationState)
        consecutiveExtensionCount = 0
        stopCountdownTicker()
        blockOverlay.clearFrictionState()
        mainHandler.post { blockOverlay.removeAfterResolution() }
    }

    fun onBreakExpired(packageName: String) {
        scope.launch {
            try {
                val profiles = profileRepository.observeActiveProfiles().first()
                val profileId = profiles.firstOrNull()?.id ?: return@launch
                val state = usageRepository.getSessionState(packageName) ?: return@launch
                val now = System.currentTimeMillis()
                val completed = SessionTracker.completeExpiredBreak(state, now)
                if (completed != state) {
                    usageRepository.saveSessionState(completed, profileId)
                    sessionStartedForPackage = packageName
                    recordFrictionEnd(packageName, profileId)
                }
                if (currentForegroundPackage == packageName) {
                    evaluate(packageName)
                }
            } catch (e: Exception) {
                enforcementLog.logError("Break expired handling failed", e)
            }
        }
    }

    fun refresh() {
        val pkg = currentForegroundPackage ?: usageStatsCollector.getForegroundPackageFallback()
        if (pkg != null) {
            scope.launch {
                try {
                    if (currentForegroundPackage != pkg) {
                        val prev = currentForegroundPackage
                        currentForegroundPackage = pkg
                        if (prev != null &&
                            prev in monitoredPackagesCache &&
                            prev != pkg &&
                            sessionStartedForPackage == prev
                        ) {
                            sessionStartedForPackage = null
                        }
                    }
                    if (enforcementLoopRunnable != null &&
                        countdownPackageName == pkg &&
                        pkg == currentForegroundPackage
                    ) {
                        mainHandler.post { refreshCountdownNotification() }
                    } else {
                        evaluate(pkg)
                    }
                } catch (e: Exception) {
                    enforcementLog.logError("Refresh failed", e)
                }
            }
        }
    }

    fun grantExtensionMinutes(packageName: String, minutes: Int) {
        scope.launch {
            try {
                val profiles = profileRepository.observeActiveProfiles().first()
                val profile = profiles.firstOrNull() ?: return@launch
                val profileId = profile.id
                val policy = profile.extensionPolicy
                val dayStart = usageStatsCollector.dayStartEpochMs()
                val overridesToday = usageRepository.countOverridesForPackageToday(
                    profileId, packageName, dayStart,
                )
                val decision = ExtensionPolicyEvaluator.evaluateExtension(
                    policy = policy,
                    requestedMinutes = minutes,
                    overridesToday = overridesToday,
                    consecutiveInSession = consecutiveExtensionCount,
                )
                when (decision) {
                    is ExtensionPolicyEvaluator.ExtensionDecision.Denied -> {
                        val denialMessage = BlockMessageResolver.extensionDenied(localizedContext, decision.reason)
                        val request = buildBlockRequest(
                            packageName = packageName,
                            message = denialMessage,
                            reason = "extensionDenied",
                            profile = profile,
                            blocked = RuleResult.Blocked(
                                reason = BlockReason.dailyLimit,
                                bypassAllowed = true,
                            ),
                            useExtensions = true,
                        )
                        enterBlockState(packageName)
                        val generation = blockGeneration
                        mainHandler.post {
                            if (generation != blockGeneration) return@post
                            blockOverlay.show(request)
                        }
                    }
                    is ExtensionPolicyEvaluator.ExtensionDecision.Allowed -> {
                        applyExtension(packageName, profileId, decision.minutes * 60_000L)
                        consecutiveExtensionCount++
                    }
                    is ExtensionPolicyEvaluator.ExtensionDecision.NoLimitToday -> {
                        grantNoLimitToday(packageName)
                    }
                }
            } catch (e: Exception) {
                enforcementLog.logError("Grant extension failed", e)
            }
        }
    }

    fun grantNoLimitToday(packageName: String) {
        scope.launch {
            try {
                val profiles = profileRepository.observeActiveProfiles().first()
                val profile = profiles.firstOrNull() ?: return@launch
                val profileId = profile.id
                val now = System.currentTimeMillis()
                val dayStart = usageStatsCollector.dayStartEpochMs(now)
                val overridesToday = usageRepository.countOverridesForPackageToday(
                    profileId, packageName, dayStart,
                )
                val decision = ExtensionPolicyEvaluator.evaluateExtension(
                    policy = profile.extensionPolicy,
                    requestedMinutes = 0,
                    overridesToday = overridesToday,
                    consecutiveInSession = consecutiveExtensionCount,
                    isNoLimitTodayRequest = true,
                )
                if (decision is ExtensionPolicyEvaluator.ExtensionDecision.Denied) return@launch
                val dayEnd = dayStart + 86_400_000L
                usageRepository.addNoLimitTodayPause(profileId, packageName, dayEnd, now)
                usageRepository.logOverride(packageName, profileId, "noLimitToday", 0L)
                clearBlockState()
                evaluate(packageName)
            } catch (e: Exception) {
                enforcementLog.logError("No limit today failed", e)
            }
        }
    }

    fun onOpenGatePassed(packageName: String) {
        openGatePassedPackage = packageName
        blockPresentationState = BlockPresentationReducer.onBlockCleared(blockPresentationState)
        blockOverlay.clearFrictionState()
        blockOverlay.removeAfterResolution()
        scope.launch {
            val profiles = profileRepository.observeActiveProfiles().first()
            val profileId = profiles.firstOrNull()?.id ?: return@launch
            recordFrictionEnd(packageName, profileId)
            evaluate(packageName)
        }
    }

    private suspend fun applyExtension(packageName: String, profileId: Long, extensionMs: Long) {
        blockPresentationState = BlockPresentationReducer.onBlockCleared(blockPresentationState)
        blockOverlay.clearFrictionState()
        recordFrictionEnd(packageName, profileId)
        usageRepository.logOverride(packageName, profileId, "extension", extensionMs)
        val session = usageRepository.getSessionState(packageName)
            ?: SessionTracker.startSession(packageName, System.currentTimeMillis())
        val cleared = SessionTracker.clearBreak(session)
        val extended = SessionTracker.addExcludedTime(cleared, extensionMs)
        usageRepository.saveSessionState(extended, profileId)
        sessionStartedForPackage = packageName
        blockOverlay.removeAfterResolution()
        evaluate(packageName)
    }

    /** @deprecated use grantExtensionMinutes */
    fun grantExtension(packageName: String, extensionMs: Long) {
        grantExtensionMinutes(packageName, (extensionMs / 60_000).toInt().coerceAtLeast(1))
    }

    fun onFrictionCompleted(packageName: String) {
        scope.launch {
            val profiles = profileRepository.observeActiveProfiles().first()
            val profileId = profiles.firstOrNull()?.id ?: return@launch
            recordFrictionEnd(packageName, profileId)
        }
    }

    fun onBlockDismissed() {
        blockPresentationState = BlockPresentationReducer.onBlockCleared(blockPresentationState)
        consecutiveExtensionCount = 0
        blockOverlay.clearFrictionState()
    }

    fun onOpenGatekeepFromOverlay() {
        blockPresentationState = BlockPresentationReducer.onBlockCleared(blockPresentationState)
        blockOverlay.clearFrictionState()
        mainHandler.post { blockOverlay.removeAfterResolution() }
    }

    fun onGatekeepActivityForegrounded() {
        clearBlockState()
    }

    fun onAccessibilityConnected() {
        stopEnforcementService()
        startForegroundPolling()
    }

    fun onAccessibilityDisconnected() {
        stopForegroundPolling()
        scope.launch {
            val settings = settingsRepository.settings.first()
            if (settings.enforcementEnabled) {
                startEnforcementService()
            }
        }
    }

    fun startEnforcementService() {
        if (ForegroundMonitorAccessibilityService.instance != null) {
            stopEnforcementService()
            return
        }
        val intent = Intent(context, EnforcementForegroundService::class.java)
        context.startForegroundService(intent)
    }

    fun stopEnforcementService() {
        val intent = Intent(context, EnforcementForegroundService::class.java).apply {
            action = EnforcementForegroundService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun pollForeground() {
        pollForegroundIfChanged()
    }

    fun pollFallbackForeground() {
        pollForegroundIfChanged()
    }

    fun stopCountdownTicker() {
        stopEnforcementLoop()
    }

    private fun stopEnforcementLoop() {
        enforcementLoopRunnable?.let { mainHandler.removeCallbacks(it) }
        enforcementLoopRunnable = null
        countdownRunnable = null
        sessionDeadlineMs = null
        dailyDeadlineMs = null
        hourlyDeadlineMs = null
        weeklyDeadlineMs = null
        countdownDailyLimitMs = null
        countdownHourlyLimitMs = null
        countdownWeeklyLimitMs = null
        countdownUsedTodayMs = null
        countdownAppLabel = null
        countdownPackageName = null
        showCountdownNotification = false
        notificationHelper.hideCountdown()
    }

    private suspend fun applyScreenOffExclusion(screenOffDurationMs: Long) {
        val pkg = currentForegroundPackage ?: return
        val state = usageRepository.getSessionState(pkg) ?: return
        if (previousProfileId <= 0) return
        usageRepository.saveSessionState(
            SessionTracker.addExcludedTime(state, screenOffDurationMs),
            previousProfileId,
        )
        if (sessionDeadlineMs != null) {
            sessionDeadlineMs = sessionDeadlineMs!! + screenOffDurationMs
        }
        if (dailyDeadlineMs != null) {
            dailyDeadlineMs = dailyDeadlineMs!! + screenOffDurationMs
        }
        if (hourlyDeadlineMs != null) {
            hourlyDeadlineMs = hourlyDeadlineMs!! + screenOffDurationMs
        }
        if (weeklyDeadlineMs != null) {
            weeklyDeadlineMs = weeklyDeadlineMs!! + screenOffDurationMs
        }
        if (showCountdownNotification) {
            mainHandler.post { refreshCountdownNotification() }
        }
    }

    private suspend fun finalizePreviousSession() {
        val prev = previousForegroundPackage ?: currentForegroundPackage ?: return
        val now = System.currentTimeMillis()
        if (countdownPackageName == prev) {
            val deadlinePassed = listOfNotNull(
                sessionDeadlineMs,
                dailyDeadlineMs,
                hourlyDeadlineMs,
                weeklyDeadlineMs,
            ).any { now >= it }
            if (deadlinePassed) {
                evaluate(prev)
                return
            }
        }
        if (previousSessionStartMs > 0 && previousProfileId > 0) {
            if (now > previousSessionStartMs) {
                usageRepository.recordSession(prev, previousProfileId, previousSessionStartMs, now)
            }
        }
        val oldState = usageRepository.getSessionState(prev)
        if (oldState != null && previousProfileId > 0) {
            val now = System.currentTimeMillis()
            val onBreak = oldState.breakUntilEpochMs?.let { now < it } == true
            val updated = if (onBreak) {
                oldState
            } else {
                SessionTracker.startSession(prev, now).copy(
                    excludedMs = oldState.excludedMs,
                    frictionStartedAtEpochMs = oldState.frictionStartedAtEpochMs,
                )
            }
            usageRepository.saveSessionState(updated, previousProfileId)
        }
    }

    private suspend fun evaluate(packageName: String) {
        evaluateMutex.withLock {
            evaluateInternal(packageName)
        }
    }

    private suspend fun evaluateInternal(packageName: String) {
        lastForegroundEvaluationAtMs[packageName] = System.currentTimeMillis()
        val evaluationToken = EvaluationToken(packageName, blockGeneration)
        val settings = settingsRepository.settings.first()
        if (!settings.enforcementEnabled) {
            stopCountdownTicker()
            clearBlockState()
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
            if (isBlockingActive && blockedPackage != null) {
                transitionToHiddenForOtherApp()
                return
            }
            stopCountdownTicker()
            blockPresentationState = BlockPresentationReducer.onBlockCleared(blockPresentationState)
            mainHandler.post { blockOverlay.removeAfterResolution() }
            return
        }

        lastMonitoredForegroundPackage = packageName
        lastMonitoredForegroundAtMs = now

        if (isEssential) {
            stopCountdownTicker()
            blockPresentationState = BlockPresentationReducer.onBlockCleared(blockPresentationState)
            blockOverlay.clearFrictionState()
            mainHandler.post { blockOverlay.removeAfterResolution() }
            return
        }

        val scheduleAllowed = ProfileMergeEngine.isWithinMergedSchedule(
            windows = allScheduleWindows,
            packageName = packageName,
            profileIds = activeProfileIds,
            nowEpochMs = now,
        )

        val primaryProfile = matchingProfiles.first()

        if (!scheduleAllowed) {
            val blockMessage = BlockMessageResolver.blockMessage(localizedContext, BlockReason.outsideSchedule)
            showBlocked(
                packageName, blockMessage, "outsideSchedule", null,
                primaryProfile, null, RuleResult.Blocked(
                    reason = BlockReason.outsideSchedule,
                    bypassAllowed = false,
                ),
            )
            return
        }

        val profileNeedingPin = matchingProfiles.firstOrNull {
            it.onOpenAction == OnOpenAction.pinGate && !it.passwordHash.isNullOrBlank()
        }
        if (profileNeedingPin != null && !profileUnlockCache.isUnlocked(profileNeedingPin.id, now)) {
            scope.launch {
                recordFrictionStart(packageName, profileNeedingPin.id)
            }
            showPinGate(
                packageName = packageName,
                profile = profileNeedingPin,
                message = BlockMessageResolver.enterProfilePin(localizedContext, appLabel),
            )
            return
        }

        val config = primaryProfile.enforcementConfig()

        if (openGatePassedPackage != packageName &&
            config.onOpenAction != OnOpenAction.none &&
            config.onOpenAction != OnOpenAction.pinGate
        ) {
            // open deterrent evaluated in RuleEngine
        }

        val mergedLimit = ProfileMergeEngine.mergedLimitForApp(limitsForMerge, packageName)
        val usage = usageStatsCollector.getUsageSnapshot(packageName, now)

        val sessionStateRaw = usageRepository.getSessionState(packageName)
        previousProfileId = primaryProfile.id

        val sessionState = ensureSessionStarted(
            packageName = packageName,
            profileId = primaryProfile.id,
            now = now,
            existingState = sessionStateRaw,
        )
        val sessionForEval = resolveSessionAfterBreak(
            packageName = packageName,
            profileId = primaryProfile.id,
            sessionState = sessionState,
            now = now,
        )

        if (SessionTracker.hasPendingWait(sessionForEval, now)) {
            val remainingSec = ((SessionTracker.pendingWaitRemainingMs(sessionForEval, now) + 999) / 1000)
                .toInt().coerceAtLeast(1)
            showPendingWait(
                packageName = packageName,
                appLabel = appLabel,
                profile = primaryProfile,
                waitSeconds = remainingSec,
                sessionState = sessionForEval,
            )
            return
        }

        if (openGatePassedPackage == packageName) {
            // skip open deterrent re-check
        }

        val evalContext = RuleEvaluationContext(
            nowEpochMs = now,
            packageName = packageName,
            profile = primaryProfile,
            limit = mergedLimit,
            isMonitored = true,
            usage = usage,
            sessionState = sessionForEval,
            pauses = pauses,
            scheduleWindows = emptyList(),
            focusModeUntilMs = settings.focusModeUntilMs,
        )

        val result = when (val ruleResult = RuleEngine.evaluate(evalContext)) {
            is RuleResult.Blocked -> ruleResult
            is RuleResult.DelayOpen -> {
                mainHandler.post {
                    blockOverlay.showDelay(
                        ruleResult.delaySeconds,
                        BlockMessageResolver.delayOpenMessage(localizedContext),
                    ) {
                        openGatePassedPackage = packageName
                        scope.launch { evaluate(packageName) }
                    }
                }
                return
            }
            is RuleResult.OpenDeterrent -> {
                if (openGatePassedPackage != packageName) {
                    showOpenDeterrent(packageName, appLabel, primaryProfile, ruleResult)
                    return
                }
                RuleResult.Allowed(null, null, null, null)
            }
            is RuleResult.Allowed -> ruleResult
        }

        if (currentForegroundPackage != packageName) return

        when (result) {
            is RuleResult.Allowed -> {
                previousSessionStartMs = sessionForEval.sessionStartEpochMs
                val remainingSessionMs = mergedLimit?.let { limit ->
                    when (val sessionCheck = SessionTracker.evaluateSession(limit, sessionForEval, now)) {
                        is SessionTracker.SessionCheckResult.Allowed -> sessionCheck.remainingSessionMs
                        else -> result.remainingSessionMs
                    }
                } ?: result.remainingSessionMs
                maybeShowWarning(packageName, appLabel, result.warningLevel, settings.warningAlertsEnabled, now)
                if (result.notifyLimitReached) {
                    val notifyReason = result.notifyLimitReason ?: BlockReason.dailyLimit
                    val profileId = primaryProfile.id
                    val isSessionNotify = notifyReason in setOf(
                        BlockReason.sessionLimit,
                        BlockReason.onBreak,
                    )
                    val shouldNotify = if (isSessionNotify) {
                        !sessionForEval.sessionLimitNotified
                    } else {
                        val notifyKey = notifyLimitKey(
                            profileId, packageName, notifyReason, now, sessionForEval.sessionStartEpochMs,
                        )
                        notifyKey !in notifiedLimitKeys
                    }
                    if (shouldNotify) {
                        if (isSessionNotify) {
                            usageRepository.saveSessionState(
                                SessionTracker.markSessionLimitNotified(sessionForEval),
                                profileId,
                            )
                        } else {
                            val notifyKey = notifyLimitKey(
                                profileId, packageName, notifyReason, now, sessionForEval.sessionStartEpochMs,
                            )
                            notifiedLimitKeys.add(notifyKey)
                        }
                        notificationHelper.showWarning(
                            localizedContext.getString(com.gatekeep.app.R.string.limit_reached_title),
                            BlockMessageResolver.blockMessage(localizedContext, notifyReason, appLabel),
                        )
                    }
                }
                if (BlockPresentationReducer.shouldApplyAllowedClear(
                        blockPresentationState,
                        evaluationToken,
                        currentForegroundPackage,
                    )
                ) {
                    blockPresentationState = BlockPresentationReducer.onBlockCleared(blockPresentationState)
                    mainHandler.post { blockOverlay.removeAfterResolution() }
                }
                if (settings.showSessionTimerNotification) {
                    val dailyLimit = mergedLimit?.dailyLimitMs
                    val hourlyLimit = mergedLimit?.hourlyLimitMs
                    val weeklyLimit = mergedLimit?.weeklyLimitMs
                    val remainingDaily = result.remainingDailyMs
                    val usedToday = if (dailyLimit != null && remainingDaily != null) {
                        (dailyLimit - remainingDaily).coerceAtLeast(0)
                    } else null
                    startEnforcementLoop(
                        appLabel = appLabel,
                        packageName = packageName,
                        remainingDailyMs = result.remainingDailyMs,
                        remainingSessionMs = remainingSessionMs,
                        remainingHourlyMs = result.remainingHourlyMs,
                        remainingWeeklyMs = result.remainingWeeklyMs,
                        dailyLimitMs = dailyLimit,
                        hourlyLimitMs = hourlyLimit,
                        weeklyLimitMs = weeklyLimit,
                        usedTodayMs = usedToday,
                        showNotification = true,
                    )
                } else {
                    startEnforcementLoop(
                        appLabel = appLabel,
                        packageName = packageName,
                        remainingDailyMs = result.remainingDailyMs,
                        remainingSessionMs = remainingSessionMs,
                        remainingHourlyMs = result.remainingHourlyMs,
                        remainingWeeklyMs = result.remainingWeeklyMs,
                        dailyLimitMs = mergedLimit?.dailyLimitMs,
                        hourlyLimitMs = mergedLimit?.hourlyLimitMs,
                        weeklyLimitMs = mergedLimit?.weeklyLimitMs,
                        usedTodayMs = null,
                        showNotification = false,
                    )
                }
                _state.value = EnforcementState(
                    foregroundPackage = packageName,
                    lastResult = result,
                    remainingSessionMs = remainingSessionMs,
                    remainingDailyMs = result.remainingDailyMs,
                    appLabel = appLabel,
                )
            }
            is RuleResult.Blocked -> {
                stopCountdownTicker()
                persistBreakIfNeeded(packageName, primaryProfile.id, result.breakUntilEpochMs, sessionForEval)
                val blockMessage = BlockMessageResolver.blockMessage(localizedContext, result.reason, appLabel)
                showBlocked(
                    packageName, blockMessage, result.reason.name,
                    result.breakUntilEpochMs, primaryProfile, mergedLimit, result,
                )
            }
            else -> Unit
        }
    }

    private fun maybeShowWarning(
        packageName: String,
        appLabel: String,
        warningLevel: com.gatekeep.domain.model.WarningLevel,
        enabled: Boolean,
        now: Long,
    ) {
        if (!enabled || warningLevel != com.gatekeep.domain.model.WarningLevel.eightyPercent) return
        val dayStart = usageStatsCollector.dayStartEpochMs(now)
        if (warnedDayStartMs != dayStart) {
            warnedPackagesToday.clear()
            warnedDayStartMs = dayStart
        }
        val key = "$packageName:$dayStart"
        if (key in warnedPackagesToday) return
        warnedPackagesToday.add(key)
        notificationHelper.showWarning(
            localizedContext.getString(com.gatekeep.app.R.string.approaching_limit_title),
            localizedContext.getString(com.gatekeep.app.R.string.approaching_limit_body, appLabel),
        )
    }

    private suspend fun persistBreakIfNeeded(
        packageName: String,
        profileId: Long,
        breakUntilMs: Long?,
        sessionState: com.gatekeep.domain.model.SessionState?,
    ) {
        if (breakUntilMs == null) return
        val state = sessionState ?: SessionTracker.startSession(packageName, System.currentTimeMillis())
        val effectiveUntil = state.breakUntilEpochMs?.let { existing ->
            maxOf(existing, breakUntilMs)
        } ?: breakUntilMs
        usageRepository.saveSessionState(
            SessionTracker.applyBreak(state, effectiveUntil),
            profileId,
        )
    }

    private suspend fun recordFrictionStart(packageName: String, profileId: Long) {
        val state = usageRepository.getSessionState(packageName)
            ?: SessionTracker.startSession(packageName, System.currentTimeMillis())
        usageRepository.saveSessionState(SessionTracker.startFriction(state, System.currentTimeMillis()), profileId)
    }

    private suspend fun recordFrictionEnd(packageName: String, profileId: Long) {
        val state = usageRepository.getSessionState(packageName) ?: return
        usageRepository.saveSessionState(SessionTracker.endFriction(state, System.currentTimeMillis()), profileId)
    }

    private suspend fun showOpenDeterrent(
        packageName: String,
        appLabel: String,
        profile: Profile,
        deterrent: RuleResult.OpenDeterrent,
    ) {
        enterBlockState(packageName)
        val generation = blockGeneration
        mainHandler.post {
            if (generation != blockGeneration) return@post
            blockOverlay.show(
                BlockOverlayRequest(
                    packageName = packageName,
                    message = BlockMessageResolver.openDeterrentMessage(localizedContext, deterrent.method),
                    reason = "openGate",
                    bypassAllowed = true,
                    frictionMethod = deterrent.method,
                    difficulty = profile.defaultFrictionDifficulty,
                    waitDurationSeconds = profile.openWaitDurationSeconds,
                    isOpenGate = true,
                ),
            )
        }
        scope.launch { recordFrictionStart(packageName, profile.id) }
    }

    private suspend fun buildMonitoredPackageSet(profiles: List<Profile>): Set<String> {
        val set = mutableSetOf<String>()
        profiles.forEach { profile ->
            profileRepository.observeMonitoredApps(profile.id).first().forEach { set.add(it.packageName) }
        }
        return set
    }

    private fun startEnforcementLoop(
        appLabel: String,
        packageName: String,
        remainingDailyMs: Long?,
        remainingSessionMs: Long?,
        remainingHourlyMs: Long?,
        remainingWeeklyMs: Long?,
        dailyLimitMs: Long?,
        hourlyLimitMs: Long?,
        weeklyLimitMs: Long?,
        usedTodayMs: Long?,
        showNotification: Boolean,
    ) {
        enforcementLoopRunnable?.let { mainHandler.removeCallbacks(it) }
        val now = System.currentTimeMillis()
        sessionDeadlineMs = remainingSessionMs?.takeIf { it > 0 }?.let { now + it }
        dailyDeadlineMs = remainingDailyMs?.takeIf { it > 0 }?.let { now + it }
        hourlyDeadlineMs = remainingHourlyMs?.takeIf { it > 0 }?.let { now + it }
        weeklyDeadlineMs = remainingWeeklyMs?.takeIf { it > 0 }?.let { now + it }

        countdownAppLabel = appLabel
        countdownPackageName = packageName
        countdownNotificationTitle = localizedContext.getString(com.gatekeep.app.R.string.hud_usage_title, appLabel)
        lastNotificationBody = null
        countdownDailyLimitMs = dailyLimitMs
        countdownHourlyLimitMs = hourlyLimitMs
        countdownWeeklyLimitMs = weeklyLimitMs
        countdownUsedTodayMs = usedTodayMs
        showCountdownNotification = showNotification

        if (showNotification) {
            refreshCountdownNotification()
        }

        enforcementLoopRunnable = object : Runnable {
            override fun run() {
                if (foregroundPollRunnable == null) {
                    pollForegroundIfChanged()
                }
                val nowMs = System.currentTimeMillis()
                val pkg = countdownPackageName
                if (pkg != null) {
                    val deadlineReached = listOfNotNull(
                        sessionDeadlineMs,
                        dailyDeadlineMs,
                        hourlyDeadlineMs,
                        weeklyDeadlineMs,
                    ).any { nowMs >= it }
                    if (deadlineReached) {
                        scope.launch { evaluate(pkg) }
                    } else {
                        val matchesForeground = currentForegroundPackage == pkg ||
                            lastMonitoredForegroundPackage == pkg
                        if (matchesForeground && showCountdownNotification) {
                            refreshCountdownNotification()
                        }
                    }
                }
                val delayMs = enforcementLoopDelayMs(nowMs)
                if (delayMs == null) {
                    val inForeground = pkg != null && (
                        currentForegroundPackage == pkg ||
                            lastMonitoredForegroundPackage == pkg
                        )
                    if (!inForeground) {
                        stopEnforcementLoop()
                    }
                    return
                }
                mainHandler.postDelayed(this, delayMs)
            }
        }
        countdownRunnable = enforcementLoopRunnable
        val initialDelay = enforcementLoopDelayMs(now) ?: EnforcementPollInterval.FINE_INTERVAL_MS
        mainHandler.postDelayed(enforcementLoopRunnable!!, initialDelay)
    }

    private fun enforcementLoopDelayMs(nowMs: Long): Long? =
        EnforcementPollInterval.enforcementLoopIntervalMs(
            nowMs,
            listOf(sessionDeadlineMs, dailyDeadlineMs, hourlyDeadlineMs, weeklyDeadlineMs),
        )

    private fun pollForegroundIfChanged() {
        if (isBlockingActive) return
        val pkg = usageStatsCollector.getForegroundPackageFallback()
        if (pkg == null || pkg == context.packageName) return
        if (pkg in ignoredForegroundPackages) return
        if (pkg != currentForegroundPackage || shouldReevaluateForeground(pkg)) {
            onForegroundAppChanged(pkg, windowClassName = null)
        }
    }

    private fun reconcileForegroundFromUsageStats() {
        if (isBlockingActive) return
        pollForegroundIfChanged()
    }

    private fun shouldReevaluateForeground(packageName: String): Boolean {
        if (sessionStartedForPackage != packageName) return true
        val lastResume = usageStatsCollector.getLastResumeTimeMs(packageName) ?: return false
        val lastEvaluated = lastForegroundEvaluationAtMs[packageName] ?: 0L
        return lastResume > lastEvaluated
    }

    private fun startForegroundPolling() {
        stopForegroundPolling()
        foregroundPollRunnable = object : Runnable {
            override fun run() {
                pollForegroundIfChanged()
                mainHandler.postDelayed(this, FOREGROUND_POLL_MS)
            }
        }
        mainHandler.postDelayed(foregroundPollRunnable!!, FOREGROUND_POLL_MS)
    }

    private fun stopForegroundPolling() {
        foregroundPollRunnable?.let { mainHandler.removeCallbacks(it) }
        foregroundPollRunnable = null
    }

    private fun refreshCountdownNotification() {
        val label = countdownAppLabel ?: return
        if (countdownPackageName == null) return
        val title = countdownNotificationTitle
            ?: localizedContext.getString(com.gatekeep.app.R.string.hud_usage_title, label).also {
                countdownNotificationTitle = it
            }
        val now = System.currentTimeMillis()
        val sessionRemaining = sessionDeadlineMs
            ?.let { (it - now).coerceAtLeast(0) }
            ?.takeIf { it > 0 }
        val dailyRemaining = dailyDeadlineMs?.let { (it - now).coerceAtLeast(0) }
        countdownUsedTodayMs = if (countdownDailyLimitMs != null && dailyRemaining != null) {
            (countdownDailyLimitMs!! - dailyRemaining).coerceAtLeast(0)
        } else {
            countdownUsedTodayMs
        }
        val hourlyRemaining = hourlyDeadlineMs?.let { (it - now).coerceAtLeast(0) }
        val weeklyRemaining = weeklyDeadlineMs?.let { (it - now).coerceAtLeast(0) }
        val shown = notificationHelper.showCountdown(
            title = title,
            hud = UsageHudInfo(
                sessionRemainingMs = sessionRemaining,
                dailyRemainingMs = dailyRemaining,
                dailyLimitMs = countdownDailyLimitMs,
                dailyUsedMs = countdownUsedTodayMs,
                hourlyRemainingMs = hourlyRemaining,
                hourlyLimitMs = countdownHourlyLimitMs,
                weeklyRemainingMs = weeklyRemaining,
                weeklyLimitMs = countdownWeeklyLimitMs,
            ),
            lastBody = lastNotificationBody,
            onBodyPosted = { lastNotificationBody = it },
        )
        if (!shown) {
            showCountdownNotification = false
        }
    }

    private fun startCountdownTicker(
        appLabel: String,
        packageName: String,
        remainingDailyMs: Long?,
        remainingSessionMs: Long?,
        dailyLimitMs: Long?,
        usedTodayMs: Long?,
    ) {
        startEnforcementLoop(
            appLabel, packageName, remainingDailyMs, remainingSessionMs,
            null, null, dailyLimitMs, null, null, usedTodayMs, showNotification = true,
        )
    }

    private fun showPinGate(packageName: String, profile: Profile, message: String) {
        enterBlockState(packageName)
        val generation = blockGeneration
        mainHandler.post {
            if (generation != blockGeneration) return@post
            blockOverlay.show(
                BlockOverlayRequest(
                    packageName = packageName,
                    message = message,
                    reason = "profilePin",
                    bypassAllowed = true,
                    frictionMethod = FrictionMethod.password,
                    difficulty = profile.defaultFrictionDifficulty,
                    profilePasswordHash = profile.passwordHash,
                    onProfileUnlocked = {
                        profileUnlockCache.unlock(profile.id)
                        scope.launch { recordFrictionEnd(packageName, profile.id) }
                    },
                    isOpenGate = true,
                ),
            )
        }
    }

    private suspend fun showBlocked(
        packageName: String,
        message: String,
        reason: String,
        breakUntilMs: Long?,
        profile: Profile,
        limit: AppLimit?,
        blocked: RuleResult.Blocked,
    ) {
        if (blockOverlay.isFrictionInProgress()) return
        enterBlockState(packageName)
        val generation = blockGeneration
        val request = buildBlockRequest(
            packageName, message, reason, profile, blocked, breakUntilMs,
        )
        mainHandler.post {
            if (generation != blockGeneration) return@post
            blockOverlay.show(request)
        }
        scope.launch { recordFrictionStart(packageName, profile.id) }
        _state.value = EnforcementState(
            foregroundPackage = packageName,
            isBlocking = true,
            blockMessage = message,
            breakUntilMs = breakUntilMs,
        )
    }

    private suspend fun buildBlockRequest(
        packageName: String,
        message: String,
        reason: String,
        profile: Profile,
        blocked: RuleResult.Blocked,
        breakUntilMs: Long? = blocked.breakUntilEpochMs,
        useExtensions: Boolean = blocked.bypassAllowed &&
            blocked.sessionDeterrent == null &&
            reason != "outsideSchedule" &&
            reason != "extensionDenied",
    ): BlockOverlayRequest {
        val friction = blocked.sessionDeterrent ?: profile.defaultFrictionMethod
        val policy = profile.extensionPolicy
        val waitSeconds = when {
            blocked.sessionDeterrent == FrictionMethod.waitOneMin &&
                blocked.reason in setOf(
                    BlockReason.dailyLimit,
                    BlockReason.hourlyLimit,
                    BlockReason.weeklyLimit,
                ) -> profile.limitWaitDurationSeconds
            blocked.sessionDeterrent == FrictionMethod.waitOneMin -> profile.sessionWaitDurationSeconds
            reason == "openGate" -> profile.openWaitDurationSeconds
            else -> profile.sessionWaitDurationSeconds
        }
        val waitWallClock = when {
            blocked.sessionDeterrent == FrictionMethod.waitOneMin -> reason != "openGate"
            reason == "openGate" && friction == FrictionMethod.waitOneMin -> false
            else -> false
        }
        val dayStart = usageStatsCollector.dayStartEpochMs()
        val usedToday = usageRepository.countOverridesForPackageToday(
            profile.id, packageName, dayStart,
        )
        val maxConsecutive = ExtensionPolicyEvaluator.effectiveConsecutiveCap(policy)
        return BlockOverlayRequest(
            packageName = packageName,
            message = message,
            reason = reason,
            breakUntilMs = breakUntilMs,
            bypassAllowed = blocked.bypassAllowed,
            frictionMethod = friction,
            difficulty = profile.defaultFrictionDifficulty,
            waitDurationSeconds = waitSeconds,
            waitWallClock = waitWallClock,
            extensionOptionMinutes = if (useExtensions) policy.optionMinutes else emptyList(),
            showNoLimitToday = useExtensions && policy.showNoLimitToday,
            useExtensionButtons = useExtensions && policy.optionMinutes.isNotEmpty(),
            profilePasswordHash = profile.passwordHash,
            extensionsUsedToday = usedToday,
            maxExtensionsPerDay = policy.maxExtensionsPerDay,
            consecutiveExtensionsUsed = consecutiveExtensionCount,
            maxConsecutiveExtensions = maxConsecutive,
        )
    }

    fun onWaitStarted(packageName: String, waitUntilEpochMs: Long) {
        scope.launch {
            try {
                val profiles = profileRepository.observeActiveProfiles().first()
                val profileId = profiles.firstOrNull()?.id ?: return@launch
                val state = usageRepository.getSessionState(packageName)
                    ?: SessionTracker.startSession(packageName, System.currentTimeMillis())
                usageRepository.saveSessionState(
                    SessionTracker.setPendingWait(state, waitUntilEpochMs),
                    profileId,
                )
            } catch (e: Exception) {
                enforcementLog.logError("Persist wait failed", e)
            }
        }
    }

    fun onWaitCompleted(packageName: String) {
        scope.launch {
            try {
                val profiles = profileRepository.observeActiveProfiles().first()
                val profileId = profiles.firstOrNull()?.id ?: return@launch
                val state = usageRepository.getSessionState(packageName) ?: return@launch
                usageRepository.saveSessionState(SessionTracker.clearPendingWait(state), profileId)
            } catch (e: Exception) {
                enforcementLog.logError("Clear wait failed", e)
            }
        }
    }

    private suspend fun showPendingWait(
        packageName: String,
        appLabel: String,
        profile: Profile,
        waitSeconds: Int,
        sessionState: SessionState,
    ) {
        enterBlockState(packageName)
        val generation = blockGeneration
        val blocked = RuleResult.Blocked(
            reason = BlockReason.sessionLimit,
            bypassAllowed = true,
            sessionDeterrent = FrictionMethod.waitOneMin,
        )
        val request = buildBlockRequest(
            packageName = packageName,
            message = BlockMessageResolver.blockMessage(
                localizedContext, BlockReason.sessionLimit, appLabel,
            ),
            reason = BlockReason.sessionLimit.name,
            profile = profile,
            blocked = blocked,
        ).copy(
            waitDurationSeconds = waitSeconds,
            waitWallClock = true,
        )
        mainHandler.post {
            if (generation != blockGeneration) return@post
            blockOverlay.show(request)
        }
        scope.launch { recordFrictionStart(packageName, profile.id) }
    }

    private suspend fun resolveSessionAfterBreak(
        packageName: String,
        profileId: Long,
        sessionState: SessionState,
        now: Long,
    ): SessionState {
        val resolved = SessionTracker.completeExpiredBreak(sessionState, now)
        if (resolved == sessionState) return sessionState
        usageRepository.saveSessionState(resolved, profileId)
        sessionStartedForPackage = packageName
        recordFrictionEnd(packageName, profileId)
        return resolved
    }

    private suspend fun ensureSessionStarted(
        packageName: String,
        profileId: Long,
        now: Long,
        existingState: SessionState?,
    ): SessionState {
        val onBreak = existingState?.breakUntilEpochMs?.let { now < it } == true
        val pendingWait = SessionTracker.hasPendingWait(existingState, now)
        val state = when {
            pendingWait && existingState != null -> existingState
            sessionStartedForPackage != packageName && !onBreak -> {
                openGatePassedPackage = null
                val breakUntil = existingState?.breakUntilEpochMs?.takeIf { now < it }
                SessionTracker.startSession(packageName, now).copy(
                    breakUntilEpochMs = breakUntil,
                    sessionLimitNotified = false,
                )
            }
            existingState == null -> SessionTracker.startSession(packageName, now)
            else -> existingState
        }
        if (state != existingState || sessionStartedForPackage != packageName) {
            usageRepository.saveSessionState(state, profileId)
        }
        sessionStartedForPackage = packageName
        return state
    }

    private fun notifyLimitKey(
        profileId: Long,
        packageName: String,
        reason: BlockReason,
        nowEpochMs: Long,
        sessionStartEpochMs: Long? = null,
    ): String {
        val periodStart = when (reason) {
            BlockReason.sessionLimit, BlockReason.onBreak ->
                sessionStartEpochMs ?: usageStatsCollector.dayStartEpochMs(nowEpochMs)
            BlockReason.hourlyLimit -> usageStatsCollector.hourStartEpochMs(nowEpochMs)
            BlockReason.weeklyLimit -> usageStatsCollector.weekStartEpochMs(nowEpochMs)
            else -> usageStatsCollector.dayStartEpochMs(nowEpochMs)
        }
        return "$profileId:$packageName:${reason.name}:$periodStart"
    }

    companion object {
        private const val BLOCK_STABILIZATION_MS = 400L
        private const val FOREGROUND_POLL_MS = 2_000L
    }
}
