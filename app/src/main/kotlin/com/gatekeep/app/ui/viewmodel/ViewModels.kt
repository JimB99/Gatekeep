package com.gatekeep.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gatekeep.app.R
import com.gatekeep.app.data.AppUsageStat
import com.gatekeep.app.data.InstalledAppEntry
import com.gatekeep.app.data.InstalledAppsRepository
import com.gatekeep.app.data.ProfileUsageSummary
import com.gatekeep.app.data.ProfileStatsOverview
import com.gatekeep.app.data.StatsOverview
import com.gatekeep.app.data.StatsRangeKind
import com.gatekeep.app.data.StatsRepository
import com.gatekeep.app.data.StatsTimeRange
import com.gatekeep.app.data.TopAppUsage
import com.gatekeep.app.ui.components.buildPermissionState
import com.gatekeep.app.util.EnforcementLog
import com.gatekeep.app.util.LocaleController
import com.gatekeep.app.util.UsageStatsCollector
import com.gatekeep.app.util.PinStorage
import com.gatekeep.app.worker.WeeklyReportWorker
import com.gatekeep.data.locale.LocalePreferences
import com.gatekeep.data.repository.ProfileRepository
import com.gatekeep.data.repository.SettingsRepository
import com.gatekeep.data.repository.UsageRepository
import com.gatekeep.domain.AppCategories
import com.gatekeep.domain.EffectiveLimitDisplay
import com.gatekeep.domain.ExtensionGrantEngine
import com.gatekeep.domain.PolicyTimelineResolver
import com.gatekeep.domain.ProfileMergeEngine
import com.gatekeep.domain.SchedulePolicyResolver
import com.gatekeep.domain.SessionTracker
import com.gatekeep.domain.StatsPeriodKind
import com.gatekeep.domain.StatsPeriodLogic
import com.gatekeep.domain.TimeBoundaries
import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.FrictionMethod
import com.gatekeep.domain.model.MonitoredApp
import com.gatekeep.app.ui.pause.DurationChoice
import com.gatekeep.domain.model.Pause
import com.gatekeep.domain.model.PauseType
import com.gatekeep.domain.model.Profile
import com.gatekeep.domain.model.ScheduleSegment
import com.gatekeep.domain.model.ScheduleWindow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfilesHomeViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val statsRepository: StatsRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context,
    private val enforcementLog: EnforcementLog,
) : ViewModel() {

    val profiles = profileRepository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _summaries = MutableStateFlow<Map<Long, ProfileUsageSummary>>(emptyMap())
    val summaries: StateFlow<Map<Long, ProfileUsageSummary>> = _summaries.asStateFlow()

    private val _monitoredPackages = MutableStateFlow<Map<Long, List<String>>>(emptyMap())
    val monitoredPackages: StateFlow<Map<Long, List<String>>> = _monitoredPackages.asStateFlow()

    private val _permissionState = MutableStateFlow(
        buildPermissionState(context, enforcementLog, enforcementEnabled = true),
    )
    val permissionState = _permissionState.asStateFlow()

    init {
        viewModelScope.launch {
            profiles.collect { loadSummaries(it) }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _permissionState.value = buildPermissionState(
                    context,
                    enforcementLog,
                    enforcementEnabled = settings.enforcementEnabled,
                )
            }
        }
    }

    fun refreshPermissions() {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            _permissionState.value = buildPermissionState(
                context,
                enforcementLog,
                enforcementEnabled = settings.enforcementEnabled,
            )
        }
    }

    fun clearEnforcementError() {
        enforcementLog.clear()
        refreshPermissions()
    }

    fun refreshAll() {
        refreshPermissions()
        refreshSummaries()
    }

    fun refreshSummaries() {
        viewModelScope.launch { loadSummaries(profiles.value) }
    }

    private suspend fun loadSummaries(list: List<Profile>) {
        val map = mutableMapOf<Long, ProfileUsageSummary>()
        val packagesMap = mutableMapOf<Long, List<String>>()
        list.forEach { profile ->
            map[profile.id] = statsRepository.profileUsageSummary(profile.id, profile.name)
            packagesMap[profile.id] = profileRepository.observeMonitoredApps(profile.id).first()
                .map { it.packageName }
        }
        _summaries.value = map
        _monitoredPackages.value = packagesMap
    }

    fun createProfile(name: String) {
        viewModelScope.launch {
            val id = profileRepository.createProfile(name)
            profileRepository.toggleProfileActive(id, true)
        }
    }

    fun toggleActive(id: Long, active: Boolean) {
        viewModelScope.launch { profileRepository.toggleProfileActive(id, active) }
    }

    fun enableEnforcement() {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(enforcementEnabled = true) }
        }
    }
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val usageRepository: UsageRepository,
    private val usageStatsCollector: UsageStatsCollector,
    private val pinStorage: PinStorage,
    private val enforcementCoordinator: com.gatekeep.app.enforcement.EnforcementCoordinator,
    @ApplicationContext private val context: Context,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val profiles = profileRepository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _profileId = MutableStateFlow<Long?>(null)

    val monitoredPackages = _profileId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else profileRepository.observeMonitoredApps(id).map { apps -> apps.map { it.packageName } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monitoredApps = _profileId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else profileRepository.observeMonitoredApps(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scheduleWindows = _profileId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else profileRepository.observeScheduleWindows(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scheduleSegments = _profileId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else profileRepository.observeScheduleSegments(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _effectivePolicy = MutableStateFlow<PolicyTimelineResolver.EffectivePolicySnapshot?>(null)
    val effectivePolicy = _effectivePolicy.asStateFlow()

    fun refreshEffectivePolicy(profileId: Long) {
        viewModelScope.launch {
            val profile = profileRepository.observeProfiles().first().find { it.id == profileId } ?: return@launch
            val now = System.currentTimeMillis()
            val pauses = usageRepository.observeActivePauses(now).first()
            val segments = profileRepository.observeScheduleSegments(profileId).first()
            val windows = profileRepository.observeScheduleWindows(profileId).first()
            val policy = SchedulePolicyResolver.resolveForProfile(
                profile = profile,
                segments = segments,
                windows = windows,
                packageName = "",
                nowEpochMs = now,
            )
            _effectivePolicy.value = PolicyTimelineResolver.snapshot(
                profile, policy, segments, pauses, now,
            )
        }
    }

    fun bindProfile(profileId: Long) {
        _profileId.value = profileId
    }

    val appSettings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.gatekeep.data.repository.AppSettings())

    val appPasswordHash = settingsRepository.settings
        .map { it.appPasswordHash }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun requiresAppPin(): Boolean {
        val settings = appSettings.value
        return settings.hasAppPin() &&
            (settings.appLockEnabled || settings.strictMode)
    }

    fun createProfile(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = profileRepository.createProfile(name)
            onCreated(id)
        }
    }

    fun deleteProfile(id: Long) {
        viewModelScope.launch { profileRepository.deleteProfile(id) }
    }

    fun toggleProfileActive(id: Long, active: Boolean) {
        viewModelScope.launch { profileRepository.toggleProfileActive(id, active) }
    }

    fun updateProfile(profile: Profile) {
        viewModelScope.launch { profileRepository.updateProfile(profile) }
    }

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage = _saveMessage.asStateFlow()

    fun saveProfile(profile: Profile, message: String = context.getString(R.string.saved)) {
        viewModelScope.launch {
            saveProfileAwait(profile, message)
        }
    }

    suspend fun saveProfileAwait(
        profile: Profile,
        message: String = context.getString(R.string.saved),
    ) {
        profileRepository.updateProfile(profile)
        _saveMessage.value = message
    }

    suspend fun updateProfileAwait(profile: Profile) {
        profileRepository.updateProfile(profile)
    }

    suspend fun updateSegmentOverridesAwait(
        segmentId: Long,
        transform: (com.gatekeep.domain.model.SchedulePolicyOverrides) ->
            com.gatekeep.domain.model.SchedulePolicyOverrides,
    ) {
        val segment = profileRepository.getScheduleSegment(segmentId) ?: return
        profileRepository.updateScheduleSegment(
            segment.copy(
                mode = com.gatekeep.domain.model.SchedulePolicyMode.customize,
                overrides = transform(segment.overrides),
            ),
        )
    }

    fun clearSaveMessage() {
        _saveMessage.value = null
    }

    fun grantExtensionInApp(profileId: Long, packageNames: List<String>, minutes: Int) {
        viewModelScope.launch {
            grantExtensionInAppAwait(profileId, packageNames, minutes)
        }
    }

    suspend fun grantExtensionInAppAwait(profileId: Long, packageNames: List<String>, minutes: Int): Boolean {
        val profile = profileRepository.observeProfiles().first().find { it.id == profileId }
        if (profile == null || packageNames.isEmpty()) return false
        val granted = if (profile.limitUsageScope == com.gatekeep.domain.model.LimitUsageScope.sharedPool) {
            enforcementCoordinator.grantExtensionForProfileAwait(
                profileId,
                packageNames.first(),
                minutes,
            )
        } else {
            var anyGranted = false
            packageNames.forEach { pkg ->
                if (enforcementCoordinator.grantExtensionForProfileAwait(profileId, pkg, minutes)) {
                    anyGranted = true
                }
            }
            anyGranted
        }
        refreshCurrentUsage(profileId)
        return granted
    }

    fun grantNoLimitTodayInApp(profileId: Long, packageNames: List<String>) {
        viewModelScope.launch {
            grantNoLimitTodayInAppAwait(profileId, packageNames)
        }
    }

    suspend fun grantNoLimitTodayInAppAwait(profileId: Long, packageNames: List<String>) {
        if (packageNames.isEmpty()) return
        packageNames.forEach { pkg ->
            enforcementCoordinator.grantNoLimitTodayForProfileAwait(profileId, pkg, fromInApp = true)
        }
        refreshCurrentUsage(profileId)
    }

    fun resetExtensionsForProfile(profileId: Long) {
        viewModelScope.launch { resetExtensionsForProfileAwait(profileId) }
    }

    suspend fun resetExtensionsForProfileAwait(profileId: Long) {
        val dayStart = usageStatsCollector.dayStartEpochMs()
        usageRepository.clearAllowPauses(listOf(profileId))
        usageRepository.clearExtensionOverridesForProfileSince(profileId, dayStart)
        profileRepository.observeMonitoredApps(profileId).first().forEach { app ->
            usageRepository.clearSessionState(profileId, app.packageName)
        }
        refreshCurrentUsage(profileId)
    }

    enum class CurrentUsageLimitKind {
        weekly,
        daily,
        hourly,
        session,
    }

    data class CurrentUsageLimitRow(
        val kind: CurrentUsageLimitKind,
        val usageMs: Long,
        val effectiveLimitMs: Long?,
        val noLimitToday: Boolean,
    )

    data class CurrentUsageAppRow(
        val packageName: String,
        val label: String,
        val limits: List<CurrentUsageLimitRow>,
    )

    data class CurrentUsageState(
        val isSharedPool: Boolean,
        val sharedLimits: List<CurrentUsageLimitRow>,
        val perApp: List<CurrentUsageAppRow>,
    )

    private val _currentUsage = MutableStateFlow<CurrentUsageState?>(null)
    val currentUsage = _currentUsage.asStateFlow()

    fun refreshCurrentUsage(profileId: Long) {
        viewModelScope.launch { loadCurrentUsage(profileId) }
    }

    private suspend fun loadCurrentUsage(profileId: Long) {
        val profile = profileRepository.observeProfiles().first().find { it.id == profileId } ?: return
        val apps = profileRepository.observeMonitoredApps(profileId).first()
        if (apps.isEmpty()) {
            _currentUsage.value = CurrentUsageState(
                isSharedPool = profile.limitUsageScope == com.gatekeep.domain.model.LimitUsageScope.sharedPool,
                sharedLimits = emptyList(),
                perApp = emptyList(),
            )
            return
        }
        val now = System.currentTimeMillis()
        val pauses = usageRepository.observeActivePauses(now).first()
        val dayStart = usageStatsCollector.dayStartEpochMs(now)
        val hourStart = usageStatsCollector.hourStartEpochMs(now)
        val weekStart = usageStatsCollector.weekStartEpochMs(now)
        val sharedPool = profile.limitUsageScope == com.gatekeep.domain.model.LimitUsageScope.sharedPool

        suspend fun buildRows(
            packageName: String,
            limit: AppLimit,
            usage: com.gatekeep.domain.model.UsageSnapshot,
            sessionState: com.gatekeep.domain.model.SessionState?,
        ): List<CurrentUsageLimitRow> {
            val dailyBonus = if (sharedPool) {
                usageRepository.sumExtensionMsForProfileSince(profileId, dayStart)
            } else {
                usageRepository.sumExtensionMsForPackageSince(profileId, packageName, dayStart)
            }
            val hourlyBonus = if (sharedPool) {
                usageRepository.sumExtensionMsForProfileSince(profileId, hourStart)
            } else {
                usageRepository.sumExtensionMsForPackageSince(profileId, packageName, hourStart)
            }
            val weeklyBonus = if (sharedPool) {
                usageRepository.sumExtensionMsForProfileSince(profileId, weekStart)
            } else {
                usageRepository.sumExtensionMsForPackageSince(profileId, packageName, weekStart)
            }
            val graceUntil = ExtensionGrantEngine.activeGraceUntilEpochMs(
                pauses = pauses,
                profileId = profileId,
                packageName = packageName,
                nowEpochMs = now,
                sharedPool = sharedPool,
            )
            val graceRemaining = graceUntil?.let { (it - now).coerceAtLeast(0) }
            val noLimitToday = isNoLimitTodayActive(pauses, profileId, packageName, now, sharedPool)
            val sessionUsage = SessionTracker.sessionDurationMs(sessionState, now)

            return buildList {
                limit.weeklyLimitMs?.let { base ->
                    add(
                        CurrentUsageLimitRow(
                            kind = CurrentUsageLimitKind.weekly,
                            usageMs = usage.weeklyMs,
                            effectiveLimitMs = EffectiveLimitDisplay.effectiveLimitMs(
                                base, usage.weeklyMs, weeklyBonus, graceRemaining, noLimitToday,
                            ),
                            noLimitToday = noLimitToday,
                        ),
                    )
                }
                limit.dailyLimitMs?.let { base ->
                    add(
                        CurrentUsageLimitRow(
                            kind = CurrentUsageLimitKind.daily,
                            usageMs = usage.dailyMs,
                            effectiveLimitMs = EffectiveLimitDisplay.effectiveLimitMs(
                                base, usage.dailyMs, dailyBonus, graceRemaining, noLimitToday,
                            ),
                            noLimitToday = noLimitToday,
                        ),
                    )
                }
                limit.hourlyLimitMs?.let { base ->
                    add(
                        CurrentUsageLimitRow(
                            kind = CurrentUsageLimitKind.hourly,
                            usageMs = usage.hourlyMs,
                            effectiveLimitMs = EffectiveLimitDisplay.effectiveLimitMs(
                                base, usage.hourlyMs, hourlyBonus, graceRemaining, noLimitToday,
                            ),
                            noLimitToday = noLimitToday,
                        ),
                    )
                }
                limit.sessionLimitMs?.let { base ->
                    add(
                        CurrentUsageLimitRow(
                            kind = CurrentUsageLimitKind.session,
                            usageMs = sessionUsage,
                            effectiveLimitMs = EffectiveLimitDisplay.effectiveLimitMs(
                                base, sessionUsage, 0L, graceRemaining, noLimitToday,
                            ),
                            noLimitToday = noLimitToday,
                        ),
                    )
                }
            }
        }

        if (sharedPool) {
            val snapshots = apps.map { usageStatsCollector.getUsageSnapshot(it.packageName, now) }
            val usage = ProfileMergeEngine.sumUsageSnapshots(snapshots)
            val baseLimit = profile.toAppLimit(apps.first().packageName)
            val sharedLimits = buildRows(apps.first().packageName, baseLimit, usage, null)
            _currentUsage.value = CurrentUsageState(
                isSharedPool = true,
                sharedLimits = sharedLimits,
                perApp = emptyList(),
            )
        } else {
            val perApp = apps.map { app ->
                val perAppLimit = profileRepository.getLimit(profileId, app.packageName)
                val limit = ProfileMergeEngine.mergeProfileAndAppLimit(profile, app.packageName, perAppLimit)
                val usage = usageStatsCollector.getUsageSnapshot(app.packageName, now)
                val session = usageRepository.getSessionState(profileId, app.packageName)
                CurrentUsageAppRow(
                    packageName = app.packageName,
                    label = app.label,
                    limits = buildRows(app.packageName, limit, usage, session),
                )
            }
            _currentUsage.value = CurrentUsageState(
                isSharedPool = false,
                sharedLimits = emptyList(),
                perApp = perApp,
            )
        }
    }

    private fun isNoLimitTodayActive(
        pauses: List<Pause>,
        profileId: Long,
        packageName: String,
        now: Long,
        sharedPool: Boolean,
    ): Boolean {
        val active = pauses.filter {
            it.untilEpochMs > now && it.type == PauseType.noLimitToday
        }
        return if (sharedPool) {
            active.any { it.profileId == profileId && it.packageName == null }
        } else {
            active.any { it.profileId == profileId && it.packageName == packageName }
        }
    }

    fun loadProfilePin(profileId: Long): String? = pinStorage.getProfilePin(profileId)

    fun saveProfilePin(profileId: Long, pin: String) = pinStorage.setProfilePin(profileId, pin)

    fun clearProfilePin(profileId: Long) = pinStorage.clearProfilePin(profileId)

    fun toggleSegmentActive(segmentId: Long, active: Boolean) {
        viewModelScope.launch { profileRepository.toggleScheduleSegmentActive(segmentId, active) }
    }

    fun duplicateSegment(segmentId: Long) {
        viewModelScope.launch {
            val segment = profileRepository.getScheduleSegment(segmentId) ?: return@launch
            val baseLabel = segment.label ?: context.getString(R.string.schedule)
            val copyLabel = context.getString(R.string.schedule_segment_copy_format, baseLabel)
            profileRepository.duplicateScheduleSegment(segmentId, copyLabel)
        }
    }

    fun deleteSegment(segmentId: Long) {
        viewModelScope.launch { profileRepository.deleteScheduleSegment(segmentId) }
    }

    fun updateSegmentOverrides(
        segmentId: Long,
        transform: (com.gatekeep.domain.model.SchedulePolicyOverrides) ->
            com.gatekeep.domain.model.SchedulePolicyOverrides,
    ) {
        viewModelScope.launch {
            val segment = profileRepository.getScheduleSegment(segmentId) ?: return@launch
            profileRepository.updateScheduleSegment(
                segment.copy(
                    mode = com.gatekeep.domain.model.SchedulePolicyMode.customize,
                    overrides = transform(segment.overrides),
                ),
            )
        }
    }

    suspend fun saveSegmentWithWindows(segment: ScheduleSegment, windows: List<ScheduleWindow>): Long {
        val segmentId = if (segment.id > 0) {
            profileRepository.updateScheduleSegment(segment)
            segment.id
        } else {
            profileRepository.upsertScheduleSegment(segment.copy(id = 0))
        }
        val existing = profileRepository.observeScheduleWindows(segment.profileId).first()
            .filter { it.segmentId == segmentId }
        existing.forEach { profileRepository.deleteScheduleWindow(it.id) }
        windows.forEach { window ->
            profileRepository.addScheduleWindow(
                window.copy(id = 0, profileId = segment.profileId, segmentId = segmentId),
            )
        }
        return segmentId
    }
}

@HiltViewModel
class AppPickerViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val installedAppsRepository: InstalledAppsRepository,
) : ViewModel() {

    private val profileIdFlow = MutableStateFlow<Long?>(null)

    val installedApps = installedAppsRepository.apps

    private val savedMonitored = MutableStateFlow<Set<String>>(emptySet())
    private val draftMonitored = MutableStateFlow<Set<String>>(emptySet())

    val isDirty = combine(savedMonitored, draftMonitored) { saved, draft -> saved != draft }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val draftMonitoredPackages = draftMonitored.asStateFlow()

    private val _showSystemApps = MutableStateFlow(false)
    val showSystemApps = _showSystemApps.asStateFlow()

    val visibleApps = combine(installedApps, draftMonitored, showSystemApps) { installed, draft, showSystem ->
        if (showSystem) {
            installed
        } else {
            installedAppsRepository.filterVisibleApps(installed, draft)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scheduleAllowedNow = profileIdFlow
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyMap())
            else combine(
                profileRepository.observeProfiles(),
                profileRepository.observeMonitoredApps(id),
                profileRepository.observeScheduleSegments(id),
                profileRepository.observeScheduleWindows(id),
            ) { profiles, apps, segments, windows ->
                val profile = profiles.find { it.id == id } ?: return@combine emptyMap()
                val now = System.currentTimeMillis()
                apps.associate { app ->
                    val policy = SchedulePolicyResolver.resolveForProfile(
                        profile = profile,
                        segments = segments,
                        windows = windows,
                        packageName = app.packageName,
                        nowEpochMs = now,
                    )
                    app.packageName to SchedulePolicyResolver.isAppAvailable(policy)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        viewModelScope.launch { installedAppsRepository.loadIfNeeded() }
        viewModelScope.launch {
            profileIdFlow
                .flatMapLatest { id ->
                    if (id == null) flowOf(emptyList())
                    else profileRepository.observeMonitoredApps(id)
                }
                .collect { apps ->
                    val packages = apps.map { it.packageName }.toSet()
                    savedMonitored.value = packages
                    if (!isDirty.value) {
                        draftMonitored.value = packages
                    }
                }
        }
    }

    fun bindProfile(profileId: Long) {
        profileIdFlow.value = profileId
    }

    fun setShowSystemApps(show: Boolean) {
        if (_showSystemApps.value == show) return
        _showSystemApps.value = show
        viewModelScope.launch {
            installedAppsRepository.loadIfNeeded(force = true, includeSystemApps = show)
        }
    }

    fun toggleApp(packageName: String, selected: Boolean) {
        draftMonitored.value = if (selected) {
            draftMonitored.value + packageName
        } else {
            draftMonitored.value - packageName
        }
    }

    fun discardChanges() {
        draftMonitored.value = savedMonitored.value
    }

    fun commitChanges(profileId: Long) {
        viewModelScope.launch { commitChangesAwait(profileId) }
    }

    suspend fun commitChangesAwait(profileId: Long) {
        val saved = savedMonitored.value
        val draft = draftMonitored.value
        val installedByPackage = installedApps.value.associateBy { it.packageName }
        (draft - saved).forEach { packageName ->
            val app = installedByPackage[packageName] ?: return@forEach
            profileRepository.addMonitoredApp(
                MonitoredApp(
                    profileId = profileId,
                    packageName = app.packageName,
                    label = app.label,
                    category = AppCategories.categoryForPackage(app.packageName),
                ),
            )
        }
        (saved - draft).forEach { packageName ->
            profileRepository.removeMonitoredApp(profileId, packageName)
        }
        savedMonitored.value = draft
    }
}

@HiltViewModel
class LimitEditorViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    fun saveLimit(limit: AppLimit) {
        viewModelScope.launch { profileRepository.upsertLimit(limit) }
    }

    suspend fun getLimit(profileId: Long, packageName: String) =
        profileRepository.getLimit(profileId, packageName)

    fun updateMonitoredApp(app: MonitoredApp) {
        viewModelScope.launch { profileRepository.updateMonitoredApp(app) }
    }
}

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    data class ScheduleFormDraft(
        val selectedDays: Set<Int> = (0..6).toSet(),
        val startMinute: Int = 9 * 60,
        val endMinute: Int = 17 * 60,
    ) {
        fun normalizedKey(): String = selectedDays.sorted().joinToString(",") + ":$startMinute:$endMinute"
    }

    data class ScheduleEditorState(
        val savedWindows: List<ScheduleWindow> = emptyList(),
        val draftWindows: List<ScheduleWindow> = emptyList(),
        val savedForm: ScheduleFormDraft = ScheduleFormDraft(),
        val draftForm: ScheduleFormDraft = ScheduleFormDraft(),
    ) {
        val isDirty: Boolean
            get() {
                fun keys(windows: List<ScheduleWindow>) =
                    windows.map { "${it.dayOfWeek}:${it.startMinute}:${it.endMinute}" }.toSet()
                return keys(savedWindows) != keys(draftWindows)
            }
    }

    private val profileIdFlow = MutableStateFlow<Long?>(null)
    private val _editorState = MutableStateFlow(ScheduleEditorState())
    val editorState = _editorState.asStateFlow()

    val windows = profileIdFlow
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else profileRepository.observeScheduleWindows(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            windows.collect { loaded ->
                val userWindows = loaded.filter { !it.isProfileAutoSwitch }
                val current = _editorState.value
                if (!current.isDirty && windowKeys(current.savedWindows) != windowKeys(userWindows)) {
                    _editorState.value = current.copy(
                        savedWindows = userWindows,
                        draftWindows = userWindows,
                    )
                }
            }
        }
    }

    fun bindProfile(profileId: Long) {
        profileIdFlow.value = profileId
    }

    fun updateForm(transform: (ScheduleFormDraft) -> ScheduleFormDraft) {
        _editorState.value = _editorState.value.copy(
            draftForm = transform(_editorState.value.draftForm),
        )
    }

    fun addDraftWindows(windows: List<ScheduleWindow>) {
        val withIds = windows.map { window ->
            window.copy(id = nextDraftWindowId--)
        }
        _editorState.value = _editorState.value.copy(
            draftWindows = _editorState.value.draftWindows + withIds,
        )
    }

    private var nextDraftWindowId = -1L

    fun removeDraftWindows(windowIds: List<Long>) {
        _editorState.value = _editorState.value.copy(
            draftWindows = _editorState.value.draftWindows.filter { it.id !in windowIds },
        )
    }

    fun discardChanges() {
        val current = _editorState.value
        _editorState.value = current.copy(
            draftWindows = current.savedWindows,
        )
    }

    fun commitSchedule(profileId: Long) {
        viewModelScope.launch { commitScheduleAwait(profileId) }
    }

    suspend fun commitScheduleAwait(profileId: Long) {
        val state = _editorState.value
        val savedKeys = state.savedWindows.map { it.contentKey() }.toSet()
        val draftKeys = state.draftWindows.map { it.contentKey() }.toSet()

        state.savedWindows
            .filter { it.contentKey() !in draftKeys }
            .forEach { profileRepository.deleteScheduleWindow(it.id) }

        state.draftWindows
            .filter { it.contentKey() !in savedKeys }
            .forEach { window ->
                profileRepository.addScheduleWindow(
                    window.copy(id = 0, profileId = profileId),
                )
            }

        _editorState.value = state.copy(
            savedWindows = state.draftWindows,
        )
    }

    private fun windowKeys(windows: List<ScheduleWindow>): Set<String> =
        windows.map { it.contentKey() }.toSet()

    private fun ScheduleWindow.contentKey(): String =
        "$dayOfWeek:$startMinute:$endMinute"
}

@HiltViewModel
class PauseViewModel @Inject constructor(
    private val usageRepository: UsageRepository,
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val usageStatsCollector: UsageStatsCollector,
) : ViewModel() {

    private val nowMs = MutableStateFlow(System.currentTimeMillis())

    init {
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                nowMs.value = System.currentTimeMillis()
            }
        }
    }

    val profiles = profileRepository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProfiles = profileRepository.observeActiveProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activePauses = nowMs
        .flatMapLatest { now -> usageRepository.observeActivePauses(now) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.gatekeep.data.repository.AppSettings())

    fun dayEndEpochMs(now: Long = System.currentTimeMillis()): Long =
        TimeBoundaries.dayBounds(now).endExclusiveMs

    fun pauseForTargets(
        type: PauseType,
        profileIds: List<Long>?,
        packageName: String? = null,
        untilMs: Long? = null,
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            usageRepository.clearAllowPauses(profileIds)
            if (profileIds == null) {
                usageRepository.addPause(type, now, null, packageName, untilMs)
            } else {
                profileIds.forEach { profileId ->
                    usageRepository.addPause(type, now, profileId, packageName, untilMs)
                }
            }
        }
    }

    fun pauseUntil(profileIds: List<Long>?, untilMs: Long) {
        pauseForTargets(PauseType.untilDatetime, profileIds, untilMs = untilMs)
    }

    fun pauseToday(profileIds: List<Long>?) {
        val dayEnd = dayEndEpochMs()
        pauseForTargets(PauseType.noLimitToday, profileIds, untilMs = dayEnd)
    }

    fun blockForTargets(profileIds: List<Long>?, untilMs: Long) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            usageRepository.clearFocusBlocks(profileIds)
            if (profileIds == null) {
                usageRepository.addFocusBlock(null, untilMs, now)
            } else {
                profileIds.forEach { profileId ->
                    usageRepository.addFocusBlock(profileId, untilMs, now)
                }
            }
            settingsRepository.updateSettings { it.copy(focusModeUntilMs = null) }
        }
    }

    fun blockForDuration(profileIds: List<Long>?, durationMs: Long) {
        blockForTargets(profileIds, System.currentTimeMillis() + durationMs)
    }

    fun blockToday(profileIds: List<Long>?) {
        blockForTargets(profileIds, dayEndEpochMs())
    }

    fun endFocusBlock(profileIds: List<Long>?) {
        viewModelScope.launch {
            usageRepository.clearFocusBlocks(profileIds)
            settingsRepository.updateSettings { it.copy(focusModeUntilMs = null) }
        }
    }

    fun resetAllForScope(profileIds: List<Long>?) {
        viewModelScope.launch {
            usageRepository.clearAllowPauses(profileIds)
            usageRepository.clearFocusBlocks(profileIds)
            settingsRepository.updateSettings { it.copy(focusModeUntilMs = null) }
        }
    }

    fun applyAllowChoice(profileIds: List<Long>?, choice: DurationChoice) {
        when (choice) {
            is DurationChoice.PresetMinutes -> {
                val type = when (choice.minutes) {
                    5 -> PauseType.fiveMin
                    15 -> PauseType.fifteenMin
                    60 -> PauseType.sixtyMin
                    else -> PauseType.untilDatetime
                }
                if (type == PauseType.untilDatetime) {
                    pauseUntil(profileIds, System.currentTimeMillis() + choice.minutes * 60_000L)
                } else {
                    pauseForTargets(type, profileIds)
                }
            }
            is DurationChoice.CustomMinutes ->
                pauseUntil(profileIds, System.currentTimeMillis() + choice.minutes * 60_000L)
            DurationChoice.Today -> pauseToday(profileIds)
            is DurationChoice.UntilDateTime -> pauseUntil(profileIds, choice.untilEpochMs)
        }
    }

    fun applyFocusChoice(profileIds: List<Long>?, choice: DurationChoice) {
        when (choice) {
            is DurationChoice.PresetMinutes ->
                blockForDuration(profileIds, choice.minutes * 60_000L)
            is DurationChoice.CustomMinutes ->
                blockForDuration(profileIds, choice.minutes * 60_000L)
            DurationChoice.Today -> blockToday(profileIds)
            is DurationChoice.UntilDateTime -> blockForTargets(profileIds, choice.untilEpochMs)
        }
    }

    fun emergencyBypass() {
        viewModelScope.launch {
            if (settings.value.strictMode) return@launch
            val now = System.currentTimeMillis()
            settingsRepository.updateSettings { it.copy(lastEmergencyBypassEpochMs = now) }
            usageRepository.addPause(PauseType.emergencyBypass, now)
        }
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val pinStorage: PinStorage,
    @ApplicationContext private val context: Context,
    private val enforcementLog: EnforcementLog,
) : ViewModel() {

    val settings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.gatekeep.data.repository.AppSettings())

    fun loadAppPin(): String? = pinStorage.getAppPin()

    fun saveAppPin(pin: String) = pinStorage.setAppPin(pin)

    fun clearAppPin() = pinStorage.clearAppPin()

    fun update(transform: (com.gatekeep.data.repository.AppSettings) -> com.gatekeep.data.repository.AppSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings(transform)
            val updated = settingsRepository.settings.first()
            WeeklyReportWorker.schedule(context, updated)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch { settingsRepository.setOnboardingComplete(true) }
    }

    suspend fun setLanguage(languageTag: String) {
        val normalized = LocalePreferences.normalizeTag(languageTag)
        settingsRepository.updateSettings { it.copy(languageTag = normalized) }
        LocaleController.apply(normalized)
    }

    fun lastEnforcementError(): String? = enforcementLog.getLastError()

    fun clearEnforcementError() {
        enforcementLog.clear()
    }
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val statsRepository: StatsRepository,
) : ViewModel() {

    val profiles = profileRepository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _rangeKind = MutableStateFlow(StatsRangeKind.day)
    val rangeKind = _rangeKind.asStateFlow()

    private val _anchorMs = MutableStateFlow(System.currentTimeMillis())
    val anchorMs = _anchorMs.asStateFlow()

    private val _overview = MutableStateFlow(StatsOverview(0L, emptyList(), "", 1L))
    val overview = _overview.asStateFlow()

    private val _profileOverviews = MutableStateFlow<List<ProfileStatsOverview>>(emptyList())
    val profileOverviews = _profileOverviews.asStateFlow()

    private val _allTopApps = MutableStateFlow<List<TopAppUsage>>(emptyList())
    private val _visibleTopAppCount = MutableStateFlow(5)
    val topApps = combine(_allTopApps, _visibleTopAppCount) { all, count ->
        all.take(count)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val canLoadMoreTopApps = combine(_allTopApps, _visibleTopAppCount) { all, count ->
        all.size > count
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var lastStatsKey: String? = null

    private val _trackedApps = MutableStateFlow<List<AppUsageStat>>(emptyList())
    val trackedApps = _trackedApps.asStateFlow()

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward = _canGoForward.asStateFlow()

    init {
        viewModelScope.launch {
            combine(_rangeKind, _anchorMs, profiles) { kind, anchor, profileList ->
                Triple(kind, anchor, profileList)
            }.collect { (kind, anchor, profileList) ->
                _canGoForward.value = StatsPeriodLogic.canShiftForward(
                    kind.toPeriodKind(),
                    anchor,
                    System.currentTimeMillis(),
                )
                loadStats(kind, anchor, profileList)
            }
        }
    }

    fun setRangeKind(kind: StatsRangeKind) {
        _rangeKind.value = kind
    }

    fun resetToCurrentPeriod() {
        _anchorMs.value = System.currentTimeMillis()
    }

    fun shiftPeriod(forward: Boolean) {
        if (forward && !_canGoForward.value) return
        val now = System.currentTimeMillis()
        _anchorMs.value = StatsPeriodLogic.shiftAnchor(
            _rangeKind.value.toPeriodKind(),
            _anchorMs.value,
            forward,
            now,
        )
    }

    private suspend fun loadStats(
        kind: StatsRangeKind,
        anchorMs: Long,
        profileList: List<com.gatekeep.domain.model.Profile>,
    ) {
        val statsKey = "$kind:$anchorMs"
        if (statsKey != lastStatsKey) {
            lastStatsKey = statsKey
            _visibleTopAppCount.value = 5
        }
        val range = buildRange(kind, anchorMs)
        coroutineScope {
            val overviewDeferred = async { statsRepository.overviewForRange(range) }
            val topAppsDeferred = async { statsRepository.topAppsForRange(range, limit = 50) }
            val profileIds = profileList.map { it.id }
            val trackedDeferred = async {
                if (profileIds.isEmpty()) emptyList()
                else statsRepository.trackedAppsForProfiles(profileIds, range)
            }
            _overview.value = overviewDeferred.await()
            _allTopApps.value = topAppsDeferred.await()
            _trackedApps.value = trackedDeferred.await()
            val profileOverviewsDeferred = async {
                profileList
                    .sortedWith(compareByDescending<com.gatekeep.domain.model.Profile> { it.isActive }.thenBy { it.name })
                    .map { profile ->
                        async {
                            val tracked = statsRepository.trackedAppsForRange(profile.id, range)
                            ProfileStatsOverview(
                                profileId = profile.id,
                                name = profile.name,
                                isActive = profile.isActive,
                                totalUsageMs = tracked.sumOf { it.usageMs },
                                streak = statsRepository.streakForProfile(profile.id),
                                overrideCount = statsRepository.overrideCount(profile.id),
                            )
                        }
                    }.awaitAll()
            }
            _profileOverviews.value = profileOverviewsDeferred.await()
        }
    }

    fun loadMoreTopApps() {
        val visible = _visibleTopAppCount.value
        val total = _allTopApps.value.size
        if (visible < total) {
            _visibleTopAppCount.value = visible + 10
            return
        }
        viewModelScope.launch {
            val range = buildRange(_rangeKind.value, _anchorMs.value)
            val expanded = statsRepository.topAppsForRange(range, limit = 50)
            if (expanded.size > total) {
                _allTopApps.value = expanded
            }
            _visibleTopAppCount.value = visible + 10
        }
    }

    private fun buildRange(kind: StatsRangeKind, anchorMs: Long): StatsTimeRange {
        val zone = java.time.ZoneId.systemDefault()
        val zdt = java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(anchorMs), zone)
        return when (kind) {
            StatsRangeKind.day -> StatsTimeRange.SingleDay(anchorMs)
            StatsRangeKind.week -> StatsTimeRange.Week(
                zdt.get(java.time.temporal.WeekFields.ISO.weekBasedYear()),
                zdt.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear()),
            )
            StatsRangeKind.month -> StatsTimeRange.Month(zdt.year, zdt.monthValue)
            StatsRangeKind.year -> StatsTimeRange.Year(zdt.year)
        }
    }

    private fun StatsRangeKind.toPeriodKind(): StatsPeriodKind = when (this) {
        StatsRangeKind.day -> StatsPeriodKind.day
        StatsRangeKind.week -> StatsPeriodKind.week
        StatsRangeKind.month -> StatsPeriodKind.month
        StatsRangeKind.year -> StatsPeriodKind.year
    }
}
