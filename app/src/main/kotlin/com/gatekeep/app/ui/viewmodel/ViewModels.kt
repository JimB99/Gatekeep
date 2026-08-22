package com.gatekeep.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gatekeep.data.repository.ProfileRepository
import com.gatekeep.data.repository.SettingsRepository
import com.gatekeep.data.repository.UsageRepository
import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.MonitoredApp
import com.gatekeep.domain.model.Profile
import com.gatekeep.domain.model.ScheduleWindow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val usageRepository: UsageRepository,
) : ViewModel() {

    val activeProfile = profileRepository.observeActiveProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val profiles = profileRepository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _monitoredApps = MutableStateFlow<List<MonitoredApp>>(emptyList())
    val monitoredApps: StateFlow<List<MonitoredApp>> = _monitoredApps.asStateFlow()

    private val _limits = MutableStateFlow<List<AppLimit>>(emptyList())
    val limits: StateFlow<List<AppLimit>> = _limits.asStateFlow()

    fun loadForProfile(profileId: Long) {
        viewModelScope.launch {
            profileRepository.observeMonitoredApps(profileId).collect { _monitoredApps.value = it }
        }
        viewModelScope.launch {
            profileRepository.observeLimits(profileId).collect { _limits.value = it }
        }
    }

    fun activateProfile(id: Long) {
        viewModelScope.launch { profileRepository.activateProfile(id) }
    }
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    val profiles = profileRepository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createProfile(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = profileRepository.createProfile(name)
            onCreated(id)
        }
    }

    fun deleteProfile(id: Long) {
        viewModelScope.launch { profileRepository.deleteProfile(id) }
    }

    fun activateProfile(id: Long) {
        viewModelScope.launch { profileRepository.activateProfile(id) }
    }
}

@HiltViewModel
class AppPickerViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    fun addApp(app: MonitoredApp) {
        viewModelScope.launch { profileRepository.addMonitoredApp(app) }
    }

    fun removeApp(profileId: Long, packageName: String) {
        viewModelScope.launch { profileRepository.removeMonitoredApp(profileId, packageName) }
    }

    fun monitoredApps(profileId: Long) = profileRepository.observeMonitoredApps(profileId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
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

    val activeProfile = profileRepository.observeActiveProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun pause(type: com.gatekeep.domain.model.PauseType, profileId: Long?, packageName: String?) {
        viewModelScope.launch {
            usageRepository.addPause(type, System.currentTimeMillis(), profileId, packageName)
        }
    }

    fun activateFocusMode() {
        viewModelScope.launch {
            val until = System.currentTimeMillis() + 25 * 60_000L
            settingsRepository.updateSettings { it.copy(focusModeUntilMs = until) }
            usageRepository.addPause(
                com.gatekeep.domain.model.PauseType.focusMode,
                System.currentTimeMillis(),
            )
        }
    }

    fun emergencyBypass() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            settingsRepository.updateSettings {
                it.copy(lastEmergencyBypassEpochMs = now)
            }
            usageRepository.addPause(
                com.gatekeep.domain.model.PauseType.emergencyBypass,
                now,
            )
        }
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.gatekeep.data.repository.AppSettings())

    fun update(transform: (com.gatekeep.data.repository.AppSettings) -> com.gatekeep.data.repository.AppSettings) {
        viewModelScope.launch { settingsRepository.updateSettings(transform) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { settingsRepository.setOnboardingComplete(true) }
    }
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val usageRepository: UsageRepository,
) : ViewModel() {

    val activeProfile = profileRepository.observeActiveProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    suspend fun overrideCount(profileId: Long) = usageRepository.getOverrideCount(profileId)
}
