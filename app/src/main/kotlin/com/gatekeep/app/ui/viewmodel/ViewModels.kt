package com.gatekeep.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gatekeep.app.data.InstalledAppEntry
import com.gatekeep.app.data.InstalledAppsRepository
import com.gatekeep.app.data.ProfileUsageSummary
import com.gatekeep.app.data.StatsRepository
import com.gatekeep.app.ui.components.buildPermissionState
import com.gatekeep.app.util.EnforcementLog
import com.gatekeep.data.repository.ProfileRepository
import com.gatekeep.data.repository.SettingsRepository
import com.gatekeep.data.repository.UsageRepository
import com.gatekeep.domain.AppCategories
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
        list.forEach { profile ->
            map[profile.id] = statsRepository.profileUsageSummary(profile.id, profile.name)
        }
        _summaries.value = map
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
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val profiles = profileRepository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appSettings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.gatekeep.data.repository.AppSettings())

    val appPasswordHash = settingsRepository.settings
        .map { it.appPasswordHash }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun requiresAppPin(): Boolean =
        appSettings.value.appLockEnabled && appSettings.value.appPasswordHash != null

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

    fun emergencyBypass() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            settingsRepository.updateSettings { it.copy(lastEmergencyBypassEpochMs = now) }
            usageRepository.addPause(PauseType.emergencyBypass, now)
        }
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context,
    private val enforcementLog: EnforcementLog,
) : ViewModel() {

    val settings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.gatekeep.data.repository.AppSettings())

    fun update(transform: (com.gatekeep.data.repository.AppSettings) -> com.gatekeep.data.repository.AppSettings) {
        viewModelScope.launch { settingsRepository.updateSettings(transform) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { settingsRepository.setOnboardingComplete(true) }
    }

    fun lastEnforcementError(): String? = enforcementLog.getLastError()
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val statsRepository: StatsRepository,
) : ViewModel() {

    val activeProfiles = profileRepository.observeActiveProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _weeklyUsage = MutableStateFlow<List<Long>>(emptyList())
    val weeklyUsage = _weeklyUsage.asStateFlow()

    private val _streak = MutableStateFlow(com.gatekeep.domain.model.StreakInfo(0, 0, null))
    val streak = _streak.asStateFlow()

    private val _appStats = MutableStateFlow<List<com.gatekeep.app.data.AppUsageStat>>(emptyList())
    val appStats = _appStats.asStateFlow()

    private val _overrideCount = MutableStateFlow(0)
    val overrideCount = _overrideCount.asStateFlow()

    fun load(profileId: Long, profileName: String) {
        viewModelScope.launch {
            _weeklyUsage.value = statsRepository.weeklyUsageByDay(profileId)
            _streak.value = statsRepository.streakForProfile(profileId)
            _overrideCount.value = statsRepository.overrideCount(profileId)
            _appStats.value = statsRepository.profileUsageSummary(profileId, profileName).apps
        }
    }
}
