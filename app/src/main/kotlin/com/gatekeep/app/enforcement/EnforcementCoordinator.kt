package com.gatekeep.app.enforcement

import android.content.Context
import android.content.Intent
import com.gatekeep.app.util.UsageStatsCollector
import com.gatekeep.data.repository.ProfileRepository
import com.gatekeep.data.repository.SettingsRepository
import com.gatekeep.data.repository.UsageRepository
import com.gatekeep.domain.RuleEngine
import com.gatekeep.domain.ScheduleEvaluator
import com.gatekeep.domain.SessionTracker
import com.gatekeep.domain.model.RuleEvaluationContext
import com.gatekeep.domain.model.RuleResult
import com.gatekeep.domain.model.UsageSnapshot
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
    val hudVisible: Boolean = false,
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
    private val hudOverlay: SessionHudOverlayManager,
    private val notificationHelper: GatekeepNotificationHelper,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(EnforcementState())
    val state: StateFlow<EnforcementState> = _state.asStateFlow()

    private var lastForegroundPackage: String? = null
    private var lastHudUpdateMs: Long = 0
    private var sessionStartMs: Long = 0

    fun onForegroundAppChanged(packageName: String) {
        if (packageName == lastForegroundPackage) return
        lastForegroundPackage = packageName
        scope.launch { evaluate(packageName) }
    }

    fun refresh() {
        val pkg = lastForegroundPackage ?: return
        scope.launch { evaluate(pkg) }
    }

    fun grantExtension(packageName: String, extensionMs: Long) {
        scope.launch {
            val profile = profileRepository.observeActiveProfile().first() ?: return@launch
            usageRepository.logOverride(packageName, profile.id, "friction", extensionMs)
            val session = usageRepository.getSessionState(packageName)
                ?: SessionTracker.startSession(packageName, System.currentTimeMillis())
            usageRepository.saveSessionState(
                session.copy(sessionStartEpochMs = System.currentTimeMillis()),
                profile.id,
            )
            blockOverlay.hide()
            evaluate(packageName)
        }
    }

    private suspend fun evaluate(packageName: String) {
        val settings = settingsRepository.settings.first()
        if (!settings.enforcementEnabled) {
            blockOverlay.hide()
            hudOverlay.hide()
            return
        }

        val profile = profileRepository.observeActiveProfile().first() ?: return
        val monitoredApps = profileRepository.observeMonitoredApps(profile.id).first()
        val isMonitored = monitoredApps.any { it.packageName == packageName }
        val monitoredApp = monitoredApps.find { it.packageName == packageName }

        if (monitoredApp?.isWhitelistedEssential == true) {
            blockOverlay.hide()
            hudOverlay.hide()
            return
        }

        val limit = profileRepository.getLimit(profile.id, packageName)
        val scheduleWindows = profileRepository.observeAllScheduleWindows().first()
        val pauses = usageRepository.observeActivePauses(System.currentTimeMillis()).first()

        val now = System.currentTimeMillis()
        val dayStart = usageStatsCollector.dayStartEpochMs(now)
        val hourStart = usageStatsCollector.hourStartEpochMs(now)
        val weekStart = usageStatsCollector.weekStartEpochMs(now)

        val usage = UsageSnapshot(
            dailyMs = usageRepository.getDailyUsage(profile.id, packageName, dayStart),
            hourlyMs = usageRepository.getHourlyUsage(profile.id, packageName, hourStart),
            weeklyMs = usageRepository.getWeeklyUsage(profile.id, packageName, weekStart),
        )

        var sessionState = usageRepository.getSessionState(packageName)
        if (isMonitored && sessionState?.packageName != packageName) {
            sessionState = SessionTracker.startSession(packageName, now)
            sessionStartMs = now
            usageRepository.saveSessionState(sessionState, profile.id)
        } else if (isMonitored && sessionState != null) {
            sessionStartMs = sessionState.sessionStartEpochMs
        }

        val autoProfileId = ScheduleEvaluator.activeProfileIdForAutoSchedule(scheduleWindows, now)
        if (autoProfileId != null && autoProfileId != profile.id) {
            profileRepository.activateProfile(autoProfileId)
        }

        val profileWindows = profileRepository.observeScheduleWindows(profile.id).first()

        val context = RuleEvaluationContext(
            nowEpochMs = now,
            packageName = packageName,
            profile = profile,
            limit = limit,
            isMonitored = isMonitored,
            usage = usage,
            sessionState = sessionState,
            pauses = pauses,
            scheduleWindows = profileWindows,
            focusModeUntilMs = settings.focusModeUntilMs,
        )

        val result = RuleEngine.evaluate(context)
        val label = monitoredApp?.label ?: packageName

        when (result) {
            is RuleResult.Allowed -> {
                blockOverlay.hide()
                if (isMonitored && settings.hudEnabled) {
                    val shouldUpdate = now - lastHudUpdateMs > 10_000 || packageName != lastForegroundPackage
                    if (shouldUpdate) {
                        lastHudUpdateMs = now
                        hudOverlay.show(
                            appLabel = label,
                            remainingSessionMs = result.remainingSessionMs,
                            remainingDailyMs = result.remainingDailyMs,
                            opacity = settings.hudOpacity,
                        )
                    }
                } else {
                    hudOverlay.hide()
                }
                notificationHelper.updateCountdown(label, result.remainingDailyMs, result.remainingSessionMs)
                _state.value = EnforcementState(
                    foregroundPackage = packageName,
                    lastResult = result,
                    isBlocking = false,
                    hudVisible = isMonitored && settings.hudEnabled,
                    remainingSessionMs = result.remainingSessionMs,
                    remainingDailyMs = result.remainingDailyMs,
                    appLabel = label,
                )
            }
            is RuleResult.Blocked -> {
                hudOverlay.hide()
                blockOverlay.show(
                    packageName = packageName,
                    message = result.message,
                    reason = result.reason.name,
                    breakUntilMs = result.breakUntilEpochMs,
                    profile = profile,
                    limit = limit,
                )
                _state.value = EnforcementState(
                    foregroundPackage = packageName,
                    lastResult = result,
                    isBlocking = true,
                    blockMessage = result.message,
                    breakUntilMs = result.breakUntilEpochMs,
                    appLabel = label,
                )
            }
            is RuleResult.DelayOpen -> {
                blockOverlay.showDelay(
                    packageName = packageName,
                    delaySeconds = result.delaySeconds,
                    message = result.message,
                )
            }
        }
    }

    fun startEnforcementService() {
        val intent = Intent(context, EnforcementForegroundService::class.java)
        context.startForegroundService(intent)
    }
}
