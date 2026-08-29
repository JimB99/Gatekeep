package com.gatekeep.app.ui.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gatekeep.app.ui.components.AppIcon
import com.gatekeep.app.ui.components.DurationPicker
import com.gatekeep.app.ui.components.SaveChangesButton
import com.gatekeep.app.ui.components.rememberUnsavedChangesGuard
import com.gatekeep.app.ui.components.PinGateDialog
import com.gatekeep.app.R
import com.gatekeep.app.ui.viewmodel.ProfileViewModel
import com.gatekeep.domain.model.OnOpenAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileHubScreen(
    profileId: Long,
    onBack: () -> Unit,
    onEditApps: () -> Unit,
    onEditSchedule: () -> Unit,
    onEditLimits: () -> Unit,
    onEditRules: () -> Unit,
    onEditPin: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsState()
    val profile = profiles.find { it.id == profileId }
    val appPasswordHash by viewModel.appPasswordHash.collectAsState()
    val monitoredPackages by viewModel.monitoredPackages.collectAsState()
    var profileName by remember(profile?.name) { mutableStateOf(profile?.name ?: "") }
    var savedName by remember(profile?.name) { mutableStateOf(profile?.name ?: "") }
    var showDeactivatePinGate by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(profile?.name) {
        profile?.name?.let {
            profileName = it
            savedName = it
        }
    }

    val nameDirty = profileName.trim() != savedName.trim()

    LaunchedEffect(profileId) {
        viewModel.bindProfile(profileId)
    }

    if (showDeactivatePinGate && profile != null) {
        PinGateDialog(
            title = stringResource(R.string.enter_app_pin_change_profile),
            passwordHash = appPasswordHash,
            onDismiss = { showDeactivatePinGate = false },
            onVerified = {
                showDeactivatePinGate = false
                viewModel.toggleProfileActive(profile.id, false)
            },
        )
    }
    if (showDeleteConfirm && profile != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_profile_title)) },
            text = { Text(stringResource(R.string.delete_profile_message, profile.name)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteProfile(profileId)
                    onBack()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profile?.name ?: stringResource(R.string.profile)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = profileName,
                onValueChange = { profileName = it },
                label = { Text(stringResource(R.string.profile_name)) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (nameDirty) {
                Button(
                    onClick = {
                        profile?.copy(name = profileName.trim())?.let {
                            viewModel.updateProfile(it)
                            savedName = profileName.trim()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = profileName.isNotBlank(),
                ) { Text(stringResource(R.string.save_name)) }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.active), fontWeight = FontWeight.Medium)
                    Switch(
                        checked = profile?.isActive == true,
                        onCheckedChange = { active ->
                            profile?.let {
                                if (!active && viewModel.requiresAppPin()) {
                                    showDeactivatePinGate = true
                                } else {
                                    viewModel.toggleProfileActive(it.id, active)
                                }
                            }
                        },
                    )
                }
            }

            ProfileNavRow(
                title = stringResource(R.string.apps),
                subtitle = stringResource(R.string.manage_tracked_apps),
                onClick = onEditApps,
                iconPackages = monitoredPackages,
            )
            ProfileNavRow(stringResource(R.string.schedule), stringResource(R.string.allowed_hours), onEditSchedule)
            ProfileNavRow(stringResource(R.string.time_limits), stringResource(R.string.daily_session_break), onEditLimits)
            ProfileNavRow(stringResource(R.string.rules), stringResource(R.string.open_limit_extension), onEditRules)
            ProfileNavRow(stringResource(R.string.profile_pin), stringResource(R.string.pin_to_open_apps), onEditPin)

            Card(modifier = Modifier.fillMaxWidth().clickable { showDeleteConfirm = true }) {
                Text(stringResource(R.string.delete_profile), modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ProfileNavRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconPackages: List<String> = emptyList(),
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        ListItem(
            headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
            supportingContent = {
                Column {
                    Text(subtitle)
                    if (iconPackages.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            iconPackages.take(5).forEach { pkg ->
                                AppIcon(pkg, modifier = Modifier.size(24.dp))
                            }
                            if (iconPackages.size > 5) {
                                Text(
                                    "+${iconPackages.size - 5}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileLimitsScreen(
    profileId: Long,
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsState()
    val profile = profiles.find { it.id == profileId }
    val saveMessage by viewModel.saveMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    fun limitToDraftMs(value: Long?): Long = value ?: 0L
    fun draftToSavedMs(value: Long): Long? = value.takeIf { it > 0 }

    var savedDailyMs by remember(profile?.id) { mutableLongStateOf(limitToDraftMs(profile?.dailyLimitMs)) }
    var savedSessionMs by remember(profile?.id) { mutableLongStateOf(limitToDraftMs(profile?.sessionLimitMs)) }
    var savedBreakMs by remember(profile?.id) { mutableLongStateOf(limitToDraftMs(profile?.breakDurationMs)) }
    var savedHourlyMs by remember(profile?.id) { mutableLongStateOf(limitToDraftMs(profile?.hourlyLimitMs)) }
    var savedWeeklyMs by remember(profile?.id) { mutableLongStateOf(limitToDraftMs(profile?.weeklyLimitMs)) }

    var dailyMs by remember(profile?.id) { mutableLongStateOf(savedDailyMs) }
    var sessionMs by remember(profile?.id) { mutableLongStateOf(savedSessionMs) }
    var breakMs by remember(profile?.id) { mutableLongStateOf(savedBreakMs) }
    var hourlyMs by remember(profile?.id) { mutableLongStateOf(savedHourlyMs) }
    var weeklyMs by remember(profile?.id) { mutableLongStateOf(savedWeeklyMs) }

    LaunchedEffect(profile?.dailyLimitMs, profile?.sessionLimitMs, profile?.breakDurationMs, profile?.hourlyLimitMs, profile?.weeklyLimitMs) {
        if (profile == null) return@LaunchedEffect
        savedDailyMs = limitToDraftMs(profile.dailyLimitMs)
        savedSessionMs = limitToDraftMs(profile.sessionLimitMs)
        savedBreakMs = limitToDraftMs(profile.breakDurationMs)
        savedHourlyMs = limitToDraftMs(profile.hourlyLimitMs)
        savedWeeklyMs = limitToDraftMs(profile.weeklyLimitMs)
        dailyMs = savedDailyMs
        sessionMs = savedSessionMs
        breakMs = savedBreakMs
        hourlyMs = savedHourlyMs
        weeklyMs = savedWeeklyMs
    }

    val isDirty = dailyMs != savedDailyMs ||
        sessionMs != savedSessionMs ||
        breakMs != savedBreakMs ||
        hourlyMs != savedHourlyMs ||
        weeklyMs != savedWeeklyMs

    fun saveLimits() {
        profile?.copy(
            dailyLimitMs = draftToSavedMs(dailyMs),
            sessionLimitMs = draftToSavedMs(sessionMs),
            breakDurationMs = breakMs,
            hourlyLimitMs = draftToSavedMs(hourlyMs),
            weeklyLimitMs = draftToSavedMs(weeklyMs),
        )?.let { updated ->
            viewModel.saveProfile(updated)
            savedDailyMs = dailyMs
            savedSessionMs = sessionMs
            savedBreakMs = breakMs
            savedHourlyMs = hourlyMs
            savedWeeklyMs = weeklyMs
        }
    }

    fun discardChanges() {
        dailyMs = savedDailyMs
        sessionMs = savedSessionMs
        breakMs = savedBreakMs
        hourlyMs = savedHourlyMs
        weeklyMs = savedWeeklyMs
    }

    val backGuard = rememberUnsavedChangesGuard(
        isDirty = isDirty,
        onNavigateBack = onBack,
        onSave = ::saveLimits,
        onDiscardChanges = ::discardChanges,
    )

    LaunchedEffect(saveMessage) {
        saveMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSaveMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.time_limits)) },
                navigationIcon = {
                    IconButton(onClick = backGuard::navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.limits_apply_all_apps), style = MaterialTheme.typography.bodySmall)
            DurationPicker(stringResource(R.string.weekly_limit_off), weeklyMs, coarseStepMinutes = 60, fineStepMinutes = 15, onDurationChange = { weeklyMs = it })
            DurationPicker(stringResource(R.string.daily_limit), dailyMs, coarseStepMinutes = 60, fineStepMinutes = 15, onDurationChange = { dailyMs = it })
            DurationPicker(stringResource(R.string.hourly_limit_off), hourlyMs, coarseStepMinutes = 15, fineStepMinutes = 5, minutesOnly = true, onDurationChange = { hourlyMs = it })
            DurationPicker(stringResource(R.string.session_limit), sessionMs, coarseStepMinutes = 15, fineStepMinutes = 5, onDurationChange = { sessionMs = it })
            DurationPicker(stringResource(R.string.break_duration), breakMs, coarseStepMinutes = 15, fineStepMinutes = 5, onDurationChange = { breakMs = it })
            SaveChangesButton(
                visible = isDirty,
                onClick = ::saveLimits,
                label = stringResource(R.string.save_limits),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePinScreen(
    profileId: Long,
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsState()
    val profile = profiles.find { it.id == profileId }
    val saveMessage by viewModel.saveMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pin by remember { mutableStateOf("") }
    var savedPin by remember { mutableStateOf("") }
    val pinEnabled = profile?.onOpenAction == OnOpenAction.pinGate

    LaunchedEffect(profileId, profile?.passwordHash) {
        val loaded = if (!profile?.passwordHash.isNullOrBlank()) {
            viewModel.loadProfilePin(profileId).orEmpty()
        } else {
            ""
        }
        pin = loaded
        savedPin = loaded
    }

    LaunchedEffect(saveMessage) {
        saveMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSaveMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_pin)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (pinEnabled) {
                    stringResource(R.string.profile_pin_enabled_hint)
                } else {
                    stringResource(R.string.profile_pin_disabled_hint)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            ProfilePinEditor(
                pin = pin,
                savedPin = savedPin,
                onPinChange = { pin = it },
                label = stringResource(R.string.profile_pin),
                hint = stringResource(R.string.profile_pin_rules_hint),
                clearHint = stringResource(R.string.clear_pin_hint),
                profile = profile,
                viewModel = viewModel,
                pinGateActive = pinEnabled,
                onSaved = { savedPin = it },
            )
        }
    }
}


