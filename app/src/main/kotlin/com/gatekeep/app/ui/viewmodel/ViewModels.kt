package com.gatekeep.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gatekeep.app.R
import com.gatekeep.app.data.AppUsageStat
import com.gatekeep.app.data.InstalledAppEntry
import com.gatekeep.app.data.InstalledAppsRepository
import com.gatekeep.app.data.ProfileUsageSummary
import com.gatekeep.app.data.StatsOverview
import com.gatekeep.app.data.StatsRangeKind
import com.gatekeep.app.data.StatsRepository
import com.gatekeep.app.data.StatsTimeRange
import com.gatekeep.app.data.TopAppUsage
import com.gatekeep.app.ui.components.buildPermissionState
import com.gatekeep.app.util.EnforcementLog
import com.gatekeep.app.util.PinStorage
import com.gatekeep.app.util.LocaleController
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfilesHomeViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val statsRepository: StatsRepository,
    @ApplicationContext private val context: Context,
    private val enforcementLog: EnforcementLog,
) : ViewModel() {

    val profiles = profileRepository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _summaries = MutableStateFlow<Map<Long, ProfileUsageSummary>>(emptyMap())
    val summaries: StateFlow<Map<Long, ProfileUsageSummary>> = _summaries.asStateFlow()

    private val _monitoredPackages = MutableStateFlow<Map<Long, List<String>>>(emptyMap())
    val monitoredPackages: StateFlow<Map<Long, List<String>>> = _monitoredPackages.asStateFlow()

    private val _permissionState = MutableStateFlow(buildPermissionState(context, enforcementLog))
    val permissionState = _permissionState.asStateFlow()

    init {
        viewModelScope.launch {
            profiles.collect { loadSummaries(it) }
        }
    }

    fun refreshPermissions() {
        _permissionState.value = buildPermissionState(context, enforcementLog)
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

    fun windows(profileId: Long) = profileRepository.observeScheduleWindows(profileId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addWindow(window: ScheduleWindow) {
        viewModelScope.launch { profileRepository.addScheduleWindow(window) }
    }

    fun deleteWindow(id: Long) {
        viewModelScope.launch { profileRepository.deleteScheduleWindow(id) }
    }

    fun deleteWindows(ids: List<Long>) {
        viewModelScope.launch {
            ids.forEach { profileRepository.deleteScheduleWindow(it) }
        }
    }
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
        viewModelScope.launch { settingsRepository.updateSettings(transform) }
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

    private val _selectedProfileId = MutableStateFlow<Long?>(null)
    val selectedProfileId = _selectedProfileId.asStateFlow()

    private val _rangeKind = MutableStateFlow(StatsRangeKind.day)
    val rangeKind = _rangeKind.asStateFlow()

    private val _anchorMs = MutableStateFlow(System.currentTimeMillis())
    val anchorMs = _anchorMs.asStateFlow()

    private val _overview = MutableStateFlow(StatsOverview(0L, emptyList(), "", 1L))
    val overview = _overview.asStateFlow()

    private val _topApps = MutableStateFlow<List<TopAppUsage>>(emptyList())
    val topApps = _topApps.asStateFlow()

    private val _trackedApps = MutableStateFlow<List<AppUsageStat>>(emptyList())
    val trackedApps = _trackedApps.asStateFlow()

    private val _streak = MutableStateFlow(com.gatekeep.domain.model.StreakInfo(0, 0, null))
    val streak = _streak.asStateFlow()

    private val _overrideCount = MutableStateFlow(0)
    val overrideCount = _overrideCount.asStateFlow()

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward = _canGoForward.asStateFlow()

    init {
        viewModelScope.launch {
            profiles.collect { list ->
                if (_selectedProfileId.value == null) {
                    _selectedProfileId.value = list.firstOrNull { it.isActive }?.id ?: list.firstOrNull()?.id
                }
            }
        }
        viewModelScope.launch {
            combine(_rangeKind, _anchorMs, _selectedProfileId) { kind, anchor, profileId ->
                Triple(kind, anchor, profileId)
            }.collect { (kind, anchor, profileId) ->
                _canGoForward.value = StatsPeriodLogic.canShiftForward(
                    kind.toPeriodKind(),
                    anchor,
                    System.currentTimeMillis(),
                )
                loadStats(kind, anchor, profileId)
            }
        }
    }

    fun setProfileId(id: Long?) {
        _selectedProfileId.value = id
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

    private suspend fun loadStats(kind: StatsRangeKind, anchorMs: Long, profileId: Long?) {
        val range = buildRange(kind, anchorMs)
        _overview.value = statsRepository.overviewForRange(range)
        _topApps.value = statsRepository.topAppsForRange(range)
        if (profileId != null) {
            _trackedApps.value = statsRepository.trackedAppsForRange(profileId, range)
            _streak.value = statsRepository.streakForProfile(profileId)
            _overrideCount.value = statsRepository.overrideCount(profileId)
        } else {
            _trackedApps.value = emptyList()
            _streak.value = com.gatekeep.domain.model.StreakInfo(0, 0, null)
            _overrideCount.value = 0
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
