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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gatekeep.app.R
import com.gatekeep.app.ui.components.GatekeepFilterChip
import com.gatekeep.app.ui.components.TwentyFourHourClockDialog
import com.gatekeep.app.ui.viewmodel.PauseViewModel
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
    var selectedDateMs by remember { mutableStateOf<Long?>(null) }
    var pickerTarget by remember { mutableStateOf(PickerTarget.Pause) }

    var pauseCustomState by rememberSaveable(stateSaver = CustomDurationStateSaver) {
        mutableStateOf(CustomDurationState())
    }
    var focusCustomState by rememberSaveable(stateSaver = CustomDurationStateSaver) {
        mutableStateOf(CustomDurationState())
    }
    var pauseDraftChoice by remember { mutableStateOf<DurationChoice?>(null) }
    var focusDraftChoice by remember { mutableStateOf<DurationChoice?>(null) }

    val pauseActivatedMsg = stringResource(R.string.pause_activated)
    val focusActivatedMsg = stringResource(R.string.focus_block_activated)
    val pauseResetDoneMsg = stringResource(R.string.pause_reset_done)

    val canAct = pauseAll || selectedProfileIds.isNotEmpty()
    fun profileIdsForScope(): List<Long>? = if (pauseAll) null else selectedProfileIds.toList()

    val now = System.currentTimeMillis()
    val allowPauses = activePauses.filter {
        it.type != PauseType.focusBlock && it.untilEpochMs > now
    }
    val focusBlocks = activePauses.filter {
        it.type == PauseType.focusBlock && it.untilEpochMs > now
    }
    val legacyFocusUntil = settings.focusModeUntilMs?.takeIf { it > now }

    val activeAllowPause = resolveScopePause(allowPauses, profileIdsForScope(), now, focusBlock = false)
    val activeFocusPause = resolveScopePause(focusBlocks, profileIdsForScope(), now, focusBlock = true)
    val activeAllowChoice = resolveActiveDurationChoice(activeAllowPause, now)
    val activeFocusChoice = resolveActiveDurationChoice(activeFocusPause, now)
        ?: legacyFocusUntil?.let { DurationChoice.UntilDateTime(it) }
    val activeAllowUntil = activeAllowPause?.untilEpochMs
    val activeFocusUntil = activeFocusPause?.untilEpochMs ?: legacyFocusUntil

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
                        pauseDraftChoice = null
                        focusDraftChoice = null
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
                                pauseDraftChoice = null
                                focusDraftChoice = null
                            },
                            label = { Text(profile.name) },
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            PauseResetBlock(
                enabled = canAct,
                onReset = {
                    viewModel.resetAllForScope(profileIdsForScope())
                    pauseDraftChoice = null
                    focusDraftChoice = null
                    scope.launch { snackbarHostState.showSnackbar(pauseResetDoneMsg) }
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

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
                    onEndEarly = {},
                    showEndEarly = false,
                )
            }
            DurationActionGrid(
                enabled = canAct,
                activeChoice = activeAllowChoice,
                draftChoice = pauseDraftChoice,
                customState = pauseCustomState,
                onCustomStateChange = { pauseCustomState = it },
                onDraftSelect = { pauseDraftChoice = it },
                onApply = {
                    val choice = pauseDraftChoice ?: return@DurationActionGrid
                    viewModel.applyAllowChoice(profileIdsForScope(), choice)
                    pauseDraftChoice = null
                    scope.launch { snackbarHostState.showSnackbar(pauseActivatedMsg) }
                },
                onUntilDate = {
                    pickerTarget = PickerTarget.Pause
                    showDatePicker = true
                },
            )

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
                activeChoice = activeFocusChoice,
                draftChoice = focusDraftChoice,
                customState = focusCustomState,
                onCustomStateChange = { focusCustomState = it },
                onDraftSelect = { focusDraftChoice = it },
                onApply = {
                    val choice = focusDraftChoice ?: return@DurationActionGrid
                    viewModel.applyFocusChoice(profileIdsForScope(), choice)
                    focusDraftChoice = null
                    scope.launch { snackbarHostState.showSnackbar(focusActivatedMsg) }
                },
                onUntilDate = {
                    pickerTarget = PickerTarget.Focus
                    showDatePicker = true
                },
            )
        }
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
        val initialMinuteOfDay = Calendar.getInstance().let {
            it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
        }
        TwentyFourHourClockDialog(
            initialMinuteOfDay = initialMinuteOfDay,
            onDismiss = { showTimePicker = false },
            onConfirm = { minuteOfDay ->
                val cal = Calendar.getInstance().apply {
                    timeInMillis = selectedDateMs!!
                    set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
                    set(Calendar.MINUTE, minuteOfDay % 60)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val choice = DurationChoice.UntilDateTime(cal.timeInMillis)
                when (pickerTarget) {
                    PickerTarget.Pause -> pauseDraftChoice = choice
                    PickerTarget.Focus -> focusDraftChoice = choice
                }
                showTimePicker = false
                selectedDateMs = null
            },
            title = stringResource(R.string.custom_time),
        )
    }
}

private fun formatUntil(untilMs: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(untilMs))
