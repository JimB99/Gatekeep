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
import com.gatekeep.app.util.PinStorage
import com.gatekeep.app.worker.WeeklyReportWorker
import com.gatekeep.data.locale.LocalePreferences
import com.gatekeep.data.repository.ProfileRepository
import com.gatekeep.data.repository.SettingsRepository
import com.gatekeep.data.repository.UsageRepository
import com.gatekeep.domain.AppCategories
import com.gatekeep.domain.StatsPeriodKind
import com.gatekeep.domain.StatsPeriodLogic
import com.gatekeep.domain.ProfileMergeEngine
import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.FrictionMethod
import com.gatekeep.domain.model.MonitoredApp
import com.gatekeep.domain.model.PauseType
import com.gatekeep.domain.model.Profile
import com.gatekeep.domain.model.ScheduleWindow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val pinStorage: PinStorage,
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

    val scheduleWindows = _profileId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else profileRepository.observeScheduleWindows(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
            profileRepository.updateProfile(profile)
            _saveMessage.value = message
        }
    }

    fun clearSaveMessage() {
        _saveMessage.value = null
    }

    fun loadProfilePin(profileId: Long): String? = pinStorage.getProfilePin(profileId)

    fun saveProfilePin(profileId: Long, pin: String) = pinStorage.setProfilePin(profileId, pin)

    fun clearProfilePin(profileId: Long) = pinStorage.clearProfilePin(profileId)
}

@HiltViewModel
class AppPickerViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val installedAppsRepository: InstalledAppsRepository,
) : ViewModel() {

    private val profileIdFlow = MutableStateFlow<Long?>(null)

    val installedApps = installedAppsRepository.apps

    val monitoredApps = profileIdFlow
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else profileRepository.observeMonitoredApps(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visibleApps = combine(installedApps, monitoredApps) { installed, monitored ->
        installedAppsRepository.filterVisibleApps(
            installed,
            monitored.map { it.packageName }.toSet(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scheduleAllowedNow = profileIdFlow
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyMap())
            else combine(
                profileRepository.observeMonitoredApps(id),
                profileRepository.observeScheduleWindows(id),
            ) { apps, windows ->
                val now = System.currentTimeMillis()
                apps.associate { app ->
                    app.packageName to ProfileMergeEngine.isWithinMergedSchedule(
                        windows = windows,
                        packageName = app.packageName,
                        profileIds = setOf(id),
                        nowEpochMs = now,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        viewModelScope.launch { installedAppsRepository.loadIfNeeded() }
    }

    fun bindProfile(profileId: Long) {
        profileIdFlow.value = profileId
    }

    fun toggleApp(profileId: Long, app: InstalledAppEntry, selected: Boolean) {
        viewModelScope.launch {
            if (selected) {
                profileRepository.addMonitoredApp(
                    MonitoredApp(
                        profileId = profileId,
                        packageName = app.packageName,
                        label = app.label,
                        category = AppCategories.categoryForPackage(app.packageName),
                    ),
                )
            } else {
                profileRepository.removeMonitoredApp(profileId, app.packageName)
            }
        }
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
                return keys(savedWindows) != keys(draftWindows) ||
                    savedForm.normalizedKey() != draftForm.normalizedKey()
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
                if (windowKeys(current.savedWindows) != windowKeys(userWindows)) {
                    _editorState.value = current.copy(
                        savedWindows = userWindows,
                        draftWindows = userWindows,
                        savedForm = current.savedForm,
                        draftForm = current.draftForm,
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
        _editorState.value = _editorState.value.copy(
            draftWindows = _editorState.value.draftWindows + windows,
        )
    }

    fun removeDraftWindows(windowIds: List<Long>) {
        _editorState.value = _editorState.value.copy(
            draftWindows = _editorState.value.draftWindows.filter { it.id !in windowIds },
        )
    }

    fun discardChanges() {
        val current = _editorState.value
        _editorState.value = current.copy(
            draftWindows = current.savedWindows,
            draftForm = current.savedForm,
        )
    }

    fun commitSchedule(profileId: Long) {
        viewModelScope.launch {
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
                savedForm = state.draftForm,
            )
        }
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
) : ViewModel() {

    val profiles = profileRepository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProfiles = profileRepository.observeActiveProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun pause(type: PauseType, profileId: Long?, packageName: String?, untilMs: Long? = null) {
        viewModelScope.launch {
            usageRepository.addPause(type, System.currentTimeMillis(), profileId, packageName, untilMs)
        }
    }

    fun pauseForTargets(
        type: PauseType,
        profileIds: List<Long>?,
        packageName: String? = null,
        untilMs: Long? = null,
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (profileIds == null) {
                usageRepository.addPause(type, now, null, packageName, untilMs)
            } else {
                profileIds.forEach { profileId ->
                    usageRepository.addPause(type, now, profileId, packageName, untilMs)
                }
            }
        }
    }

    fun activateFocusMode() {
        viewModelScope.launch {
            val until = System.currentTimeMillis() + 25 * 60_000L
            settingsRepository.updateSettings { it.copy(focusModeUntilMs = until) }
            usageRepository.addPause(PauseType.focusMode, System.currentTimeMillis())
        }
    }

    val settings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.gatekeep.data.repository.AppSettings())

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
    private val _visibleTopAppCount = MutableStateFlow(10)
    val topApps = combine(_allTopApps, _visibleTopAppCount) { all, count ->
        all.take(count)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val canLoadMoreTopApps = combine(_allTopApps, _visibleTopAppCount) { all, count ->
        all.size > count
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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
        val range = buildRange(kind, anchorMs)
        coroutineScope {
            val overviewDeferred = async { statsRepository.overviewForRange(range) }
            val topAppsDeferred = async { statsRepository.topAppsForRange(range, limit = 10) }
            val profileIds = profileList.map { it.id }
            val trackedDeferred = async {
                if (profileIds.isEmpty()) emptyList()
                else statsRepository.trackedAppsForProfiles(profileIds, range)
            }
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
            _overview.value = overviewDeferred.await()
            _visibleTopAppCount.value = 10
            _allTopApps.value = topAppsDeferred.await()
            _trackedApps.value = trackedDeferred.await()
            _profileOverviews.value = profileOverviewsDeferred.await()
        }
    }

    fun loadMoreTopApps() {
        val current = _allTopApps.value.size
        if (current >= 50) {
            _visibleTopAppCount.value += 10
            return
        }
        viewModelScope.launch {
            val range = buildRange(_rangeKind.value, _anchorMs.value)
            _allTopApps.value = statsRepository.topAppsForRange(range, limit = 50)
            _visibleTopAppCount.value += 10
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
