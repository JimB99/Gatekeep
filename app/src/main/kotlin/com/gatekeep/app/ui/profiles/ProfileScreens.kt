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
import com.gatekeep.domain.LimitField
import com.gatekeep.domain.LimitHierarchy
import com.gatekeep.app.util.PasswordHasher
import com.gatekeep.app.util.formatDurationMinutes
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.Profile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileHubScreen(
    profileId: Long,
    onBack: () -> Unit,
    onEditApps: () -> Unit,
    onEditPolicy: () -> Unit,
    onEditPin: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsState()
    val profile = profiles.find { it.id == profileId }
    val appPasswordHash by viewModel.appPasswordHash.collectAsState()
    val monitoredPackages by viewModel.monitoredPackages.collectAsState()
    val scheduleWindows by viewModel.scheduleWindows.collectAsState()
    val scheduleSegments by viewModel.scheduleSegments.collectAsState()
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

    fun saveName() {
        profile?.copy(name = profileName.trim())?.let {
            viewModel.updateProfile(it)
            savedName = profileName.trim()
        }
    }

    val backGuard = rememberUnsavedChangesGuard(
        isDirty = nameDirty,
        onNavigateBack = onBack,
        onSave = ::saveName,
        onDiscardChanges = { profileName = savedName },
    )

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
                    onClick = ::saveName,
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
                subtitle = profileAppsSubtitle(monitoredPackages.size),
                onClick = onEditApps,
                iconPackages = monitoredPackages,
            )
            ProfileNavRow(
                title = stringResource(R.string.policy),
                subtitle = profilePolicySubtitle(profile, scheduleSegments.size),
                onClick = onEditPolicy,
            )
            ProfileNavRow(
                title = stringResource(R.string.profile_pin),
                subtitle = if (profile?.onOpenAction == OnOpenAction.pinGate) {
                    stringResource(R.string.profile_pin_on)
                } else {
                    stringResource(R.string.profile_pin_off)
                },
                onClick = onEditPin,
            )

            Card(
                modifier = Modifier.fillMaxWidth().clickable { showDeleteConfirm = true },
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                ),
            ) {
                Text(
                    stringResource(R.string.delete_profile),
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error,
                )
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
    var savedHourlyMs by remember(profile?.id) { mutableLongStateOf(limitToDraftMs(profile?.hourlyLimitMs)) }
    var savedWeeklyMs by remember(profile?.id) { mutableLongStateOf(limitToDraftMs(profile?.weeklyLimitMs)) }

    var dailyMs by remember(profile?.id) { mutableLongStateOf(savedDailyMs) }
    var sessionMs by remember(profile?.id) { mutableLongStateOf(savedSessionMs) }
    var hourlyMs by remember(profile?.id) { mutableLongStateOf(savedHourlyMs) }
    var weeklyMs by remember(profile?.id) { mutableLongStateOf(savedWeeklyMs) }

    LaunchedEffect(profile?.dailyLimitMs, profile?.sessionLimitMs, profile?.hourlyLimitMs, profile?.weeklyLimitMs) {
        if (profile == null) return@LaunchedEffect
        savedDailyMs = limitToDraftMs(profile.dailyLimitMs)
        savedSessionMs = limitToDraftMs(profile.sessionLimitMs)
        savedHourlyMs = limitToDraftMs(profile.hourlyLimitMs)
        savedWeeklyMs = limitToDraftMs(profile.weeklyLimitMs)
        dailyMs = savedDailyMs
        sessionMs = savedSessionMs
        hourlyMs = savedHourlyMs
        weeklyMs = savedWeeklyMs
    }

    val isDirty = dailyMs != savedDailyMs ||
        sessionMs != savedSessionMs ||
        hourlyMs != savedHourlyMs ||
        weeklyMs != savedWeeklyMs
    val hierarchyValidation = LimitHierarchy.validate(weeklyMs, dailyMs, hourlyMs, sessionMs)
    val hierarchyError = stringResource(R.string.limits_hierarchy_error)
    val hierarchyHint = stringResource(R.string.limits_hierarchy_hint)

    fun saveLimits() {
        if (!hierarchyValidation.valid) return
        profile?.copy(
            dailyLimitMs = draftToSavedMs(dailyMs),
            sessionLimitMs = draftToSavedMs(sessionMs),
            hourlyLimitMs = draftToSavedMs(hourlyMs),
            weeklyLimitMs = draftToSavedMs(weeklyMs),
        )?.let { updated ->
            viewModel.saveProfile(updated)
            savedDailyMs = dailyMs
            savedSessionMs = sessionMs
            savedHourlyMs = hourlyMs
            savedWeeklyMs = weeklyMs
        }
    }

    fun discardChanges() {
        dailyMs = savedDailyMs
        sessionMs = savedSessionMs
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
            Text(
                hierarchyHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DurationPicker(
                label = stringResource(R.string.weekly_limit_off),
                totalMs = weeklyMs,
                coarseStepMinutes = 60,
                fineStepMinutes = 15,
                isSet = weeklyMs > 0,
                isError = LimitField.Weekly in hierarchyValidation.invalidFields,
                onDurationChange = { weeklyMs = it },
            )
            DurationPicker(
                label = stringResource(R.string.daily_limit),
                totalMs = dailyMs,
                coarseStepMinutes = 60,
                fineStepMinutes = 15,
                isSet = dailyMs > 0,
                isError = LimitField.Daily in hierarchyValidation.invalidFields,
                onDurationChange = { dailyMs = it },
            )
            DurationPicker(
                label = stringResource(R.string.hourly_limit_off),
                totalMs = hourlyMs,
                coarseStepMinutes = 15,
                fineStepMinutes = 5,
                minutesOnly = true,
                isSet = hourlyMs > 0,
                isError = LimitField.Hourly in hierarchyValidation.invalidFields,
                supportingText = stringResource(R.string.hourly_limit_resets),
                onDurationChange = { hourlyMs = it },
            )
            DurationPicker(
                label = stringResource(R.string.session_limit),
                totalMs = sessionMs,
                coarseStepMinutes = 15,
                fineStepMinutes = 5,
                isSet = sessionMs > 0,
                isError = LimitField.Session in hierarchyValidation.invalidFields,
                onDurationChange = { sessionMs = it },
            )
            if (!hierarchyValidation.valid) {
                Text(
                    hierarchyError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
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

    val pinDirty = pin != savedPin

    fun savePin() {
        profile?.let { p ->
            val trimmed = pin.trim()
            if (trimmed.isNotBlank()) {
                viewModel.saveProfilePin(p.id, trimmed)
                viewModel.saveProfile(
                    p.copy(
                        passwordHash = PasswordHasher.hash(trimmed),
                        lockEnabled = pinEnabled,
                    ),
                )
                savedPin = trimmed
            } else {
                viewModel.clearProfilePin(p.id)
                viewModel.saveProfile(p.copy(passwordHash = null, lockEnabled = false))
                savedPin = ""
            }
        }
    }

    val backGuard = rememberUnsavedChangesGuard(
        isDirty = pinDirty,
        onNavigateBack = onBack,
        onSave = ::savePin,
        onDiscardChanges = { pin = savedPin },
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_pin)) },
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

@Composable
private fun profileAppsSubtitle(count: Int): String =
    stringResource(R.string.profile_apps_tracked, count)

@Composable
private fun profilePolicySubtitle(profile: Profile?, segmentCount: Int): String {
    if (profile == null) return stringResource(R.string.no_schedules)
    val limits = profileLimitsSubtitle(profile)
    return if (segmentCount == 0) {
        limits
    } else {
        stringResource(R.string.policy_subtitle_format, limits, segmentCount)
    }
}

@Composable
private fun profileScheduleSubtitle(windowCount: Int): String =
    if (windowCount == 0) {
        stringResource(R.string.profile_schedule_always)
    } else {
        stringResource(R.string.profile_schedule_windows, windowCount)
    }

@Composable
internal fun profileLimitsSubtitle(profile: Profile): String {
    val parts = buildList {
        profile.weeklyLimitMs?.let {
            add(stringResource(R.string.limit_weekly_short, formatDurationMinutes(it)))
        }
        profile.dailyLimitMs?.let {
            add(stringResource(R.string.limit_daily_short, formatDurationMinutes(it)))
        }
        profile.hourlyLimitMs?.let {
            add(stringResource(R.string.limit_hourly_short, formatDurationMinutes(it)))
        }
        profile.sessionLimitMs?.let {
            add(stringResource(R.string.limit_session_short, formatDurationMinutes(it)))
        }
    }
    return if (parts.isEmpty()) {
        stringResource(R.string.profile_limits_none)
    } else {
        parts.joinToString(" · ")
    }
}

@Composable
internal fun overrideLimitsSubtitle(overrides: com.gatekeep.domain.model.SchedulePolicyOverrides): String {
    val parts = buildList {
        overrides.weeklyLimitMs?.let {
            add(stringResource(R.string.limit_weekly_short, formatDurationMinutes(it)))
        }
        overrides.dailyLimitMs?.let {
            add(stringResource(R.string.limit_daily_short, formatDurationMinutes(it)))
        }
        overrides.hourlyLimitMs?.let {
            add(stringResource(R.string.limit_hourly_short, formatDurationMinutes(it)))
        }
        overrides.sessionLimitMs?.let {
            add(stringResource(R.string.limit_session_short, formatDurationMinutes(it)))
        }
    }
    return if (parts.isEmpty()) {
        stringResource(R.string.profile_limits_none)
    } else {
        parts.joinToString(" · ")
    }
}

@Composable
internal fun overrideRulesSubtitle(overrides: com.gatekeep.domain.model.SchedulePolicyOverrides): String {
    val parts = buildList {
        overrides.onOpenAction?.let { add(openActionLabel(it)) }
        overrides.onLimitAction?.let { add(limitActionLabel(it)) }
        overrides.onSessionLimitAction?.let { add(sessionActionLabel(it)) }
    }
    return if (parts.isEmpty()) {
        stringResource(R.string.customize_rules_inherit)
    } else {
        parts.joinToString(" · ")
    }
}

@Composable
private fun profileRulesSubtitle(profile: Profile): String =
    "${openActionLabel(profile.onOpenAction)} · ${limitActionLabel(profile.onLimitAction)} · ${sessionActionLabel(profile.onSessionLimitAction)}"


