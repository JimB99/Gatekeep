package com.gatekeep.app.ui.pause

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gatekeep.app.R
import com.gatekeep.app.ui.components.DurationPicker
import com.gatekeep.app.ui.components.GatekeepFilterChip
import com.gatekeep.app.ui.viewmodel.PauseViewModel
import com.gatekeep.domain.model.Pause
import com.gatekeep.domain.model.PauseType
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

private enum class PickerTarget { Pause, Focus }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PauseScreen(
    onBack: () -> Unit,
    viewModel: PauseViewModel = hiltViewModel(),
) {
    val activeProfiles by viewModel.activeProfiles.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val activePauses by viewModel.activePauses.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var pauseAll by remember { mutableStateOf(true) }
    var selectedProfileIds by remember { mutableStateOf(setOf<Long>()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showCustomPicker by remember { mutableStateOf(false) }
    var selectedDateMs by remember { mutableStateOf<Long?>(null) }
    var pickerTarget by remember { mutableStateOf(PickerTarget.Pause) }
    var customDurationMs by remember { mutableLongStateOf(30 * 60_000L) }

    val pauseActivatedMsg = stringResource(R.string.pause_activated)
    val focusActivatedMsg = stringResource(R.string.focus_block_activated)

    val canAct = pauseAll || selectedProfileIds.isNotEmpty()
    fun profileIdsForScope(): List<Long>? = if (pauseAll) null else selectedProfileIds.toList()

    suspend fun notifyPauseActivated() {
        snackbarHostState.showSnackbar(pauseActivatedMsg)
    }

    suspend fun notifyFocusActivated() {
        snackbarHostState.showSnackbar(focusActivatedMsg)
    }

    val now = System.currentTimeMillis()
    val allowPauses = activePauses.filter {
        it.type != PauseType.focusBlock && it.untilEpochMs > now
    }
    val focusBlocks = activePauses.filter {
        it.type == PauseType.focusBlock && it.untilEpochMs > now
    }
    val legacyFocusUntil = settings.focusModeUntilMs?.takeIf { it > now }

    val activeAllowUntil = resolveActiveUntil(allowPauses, profileIdsForScope(), now)
    val activeFocusUntil = resolveActiveUntil(focusBlocks, profileIdsForScope(), now)
        ?: legacyFocusUntil

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pause_limits)) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.scope))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GatekeepFilterChip(
                    selected = pauseAll,
                    onClick = {
                        pauseAll = true
                        selectedProfileIds = emptySet()
                    },
                    label = { Text(stringResource(R.string.scope_all)) },
                )
            }
            Text(
                stringResource(R.string.scope_active_profiles),
                style = MaterialTheme.typography.labelMedium,
            )
            if (activeProfiles.isEmpty()) {
                Text(
                    stringResource(R.string.pause_no_active_profiles),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    activeProfiles.forEach { profile ->
                        GatekeepFilterChip(
                            selected = !pauseAll && profile.id in selectedProfileIds,
                            onClick = {
                                pauseAll = false
                                selectedProfileIds = if (profile.id in selectedProfileIds) {
                                    selectedProfileIds - profile.id
                                } else {
                                    selectedProfileIds + profile.id
                                }
                            },
                            label = { Text(profile.name) },
                        )
                    }
                }
            }

            PauseSectionHeader(
                title = stringResource(R.string.pause_enforcement_section),
                help = stringResource(R.string.pause_enforcement_help),
            )
            if (activeAllowUntil != null) {
                ActiveUntilBanner(
                    label = stringResource(
                        R.string.pause_active_until,
                        formatUntil(activeAllowUntil),
                    ),
                    onEndEarly = {
                        // Allow pauses expire naturally; no per-scope clear API yet.
                    },
                    showEndEarly = false,
                )
            }
            DurationActionGrid(
                enabled = canAct,
                onFiveMin = {
                    viewModel.pauseForTargets(PauseType.fiveMin, profileIdsForScope())
                    scope.launch { notifyPauseActivated() }
                },
                onFifteenMin = {
                    viewModel.pauseForTargets(PauseType.fifteenMin, profileIdsForScope())
                    scope.launch { notifyPauseActivated() }
                },
                onSixtyMin = {
                    viewModel.pauseForTargets(PauseType.sixtyMin, profileIdsForScope())
                    scope.launch { notifyPauseActivated() }
                },
                onCustom = {
                    pickerTarget = PickerTarget.Pause
                    showCustomPicker = true
                },
                onToday = {
                    viewModel.pauseToday(profileIdsForScope())
                    scope.launch { notifyPauseActivated() }
                },
                onUntilDate = {
                    pickerTarget = PickerTarget.Pause
                    showDatePicker = true
                },
            )

            if (!settings.strictMode) {
                Text(
                    stringResource(R.string.pause_global_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { viewModel.emergencyBypass() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.emergency_bypass))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            PauseSectionHeader(
                title = stringResource(R.string.focus_mode_section),
                help = stringResource(R.string.focus_mode_help),
            )
            if (activeFocusUntil != null) {
                ActiveUntilBanner(
                    label = stringResource(
                        R.string.focus_block_active_until,
                        formatUntil(activeFocusUntil),
                    ),
                    onEndEarly = {
                        viewModel.endFocusBlock(profileIdsForScope())
                    },
                    showEndEarly = true,
                )
            }
            DurationActionGrid(
                enabled = canAct,
                onFiveMin = {
                    viewModel.blockForDuration(profileIdsForScope(), 5 * 60_000L)
                    scope.launch { notifyFocusActivated() }
                },
                onFifteenMin = {
                    viewModel.blockForDuration(profileIdsForScope(), 15 * 60_000L)
                    scope.launch { notifyFocusActivated() }
                },
                onSixtyMin = {
                    viewModel.blockForDuration(profileIdsForScope(), 60 * 60_000L)
                    scope.launch { notifyFocusActivated() }
                },
                onCustom = {
                    pickerTarget = PickerTarget.Focus
                    showCustomPicker = true
                },
                onToday = {
                    viewModel.blockToday(profileIdsForScope())
                    scope.launch { notifyFocusActivated() }
                },
                onUntilDate = {
                    pickerTarget = PickerTarget.Focus
                    showDatePicker = true
                },
            )
        }
    }

    if (showCustomPicker) {
        AlertDialog(
            onDismissRequest = { showCustomPicker = false },
            title = { Text(stringResource(R.string.pause_custom_duration_title)) },
            text = {
                DurationPicker(
                    label = stringResource(R.string.duration_custom),
                    totalMs = customDurationMs,
                    coarseStepMinutes = 60,
                    fineStepMinutes = 15,
                    isSet = customDurationMs > 0,
                    onDurationChange = { customDurationMs = it },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val ids = profileIdsForScope()
                    when (pickerTarget) {
                        PickerTarget.Pause -> {
                            viewModel.pauseUntil(ids, System.currentTimeMillis() + customDurationMs)
                            scope.launch { notifyPauseActivated() }
                        }
                        PickerTarget.Focus -> {
                            viewModel.blockForDuration(ids, customDurationMs)
                            scope.launch { notifyFocusActivated() }
                        }
                    }
                    showCustomPicker = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showCustomPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedDateMs = dateState.selectedDateMillis
                    showDatePicker = false
                    showTimePicker = true
                }) { Text(stringResource(R.string.next)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) { DatePicker(state = dateState) }
    }

    if (showTimePicker && selectedDateMs != null) {
        val timeState = rememberTimePickerState()
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = selectedDateMs!!
                        set(Calendar.HOUR_OF_DAY, timeState.hour)
                        set(Calendar.MINUTE, timeState.minute)
                        set(Calendar.SECOND, 0)
                    }
                    val ids = profileIdsForScope()
                    when (pickerTarget) {
                        PickerTarget.Pause -> {
                            viewModel.pauseUntil(ids, cal.timeInMillis)
                            scope.launch { notifyPauseActivated() }
                        }
                        PickerTarget.Focus -> {
                            viewModel.blockForTargets(ids, cal.timeInMillis)
                            scope.launch { notifyFocusActivated() }
                        }
                    }
                    showTimePicker = false
                    selectedDateMs = null
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
            text = { TimePicker(state = timeState) },
        )
    }
}

@Composable
private fun ActiveUntilBanner(
    label: String,
    onEndEarly: () -> Unit,
    showEndEarly: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        if (showEndEarly) {
            TextButton(onClick = onEndEarly) {
                Text(stringResource(R.string.end_early))
            }
        }
    }
}

private fun resolveActiveUntil(
    pauses: List<Pause>,
    profileIds: List<Long>?,
    now: Long,
): Long? {
    val active = pauses.filter { it.untilEpochMs > now && it.packageName == null }
    return when {
        profileIds == null -> active.firstOrNull { it.profileId == null }?.untilEpochMs
            ?: active.maxOfOrNull { it.untilEpochMs }
        profileIds.size == 1 -> active.firstOrNull { it.profileId == profileIds.first() }?.untilEpochMs
        else -> active.filter { it.profileId in profileIds }.maxOfOrNull { it.untilEpochMs }
    }
}

private fun formatUntil(untilMs: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(untilMs))
