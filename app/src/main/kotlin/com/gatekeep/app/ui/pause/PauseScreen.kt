package com.gatekeep.app.ui.pause

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gatekeep.app.R
import com.gatekeep.app.ui.components.GatekeepFilterChip
import com.gatekeep.app.ui.viewmodel.PauseViewModel
import com.gatekeep.domain.model.PauseType
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PauseScreen(
    onBack: () -> Unit,
    viewModel: PauseViewModel = hiltViewModel(),
) {
    val activeProfiles by viewModel.activeProfiles.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var pauseAll by remember { mutableStateOf(true) }
    var selectedProfileIds by remember { mutableStateOf(setOf<Long>()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedDateMs by remember { mutableStateOf<Long?>(null) }

    val canPauseScoped = pauseAll || selectedProfileIds.isNotEmpty()

    fun profileIdsForPause(): List<Long>? =
        if (pauseAll) null else selectedProfileIds.toList()

    fun pauseQuick(type: PauseType) {
        viewModel.pauseForTargets(type, profileIdsForPause())
    }

    Scaffold(
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
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
            PauseButton(
                label = stringResource(R.string.pause_5_min),
                enabled = canPauseScoped,
                onClick = { pauseQuick(PauseType.fiveMin) },
            )
            PauseButton(
                label = stringResource(R.string.pause_15_min),
                enabled = canPauseScoped,
                onClick = { pauseQuick(PauseType.fifteenMin) },
            )
            PauseButton(
                label = stringResource(R.string.pause_60_min),
                enabled = canPauseScoped,
                onClick = { pauseQuick(PauseType.sixtyMin) },
            )
            PauseButton(
                label = stringResource(R.string.pause_until_date),
                enabled = canPauseScoped,
                onClick = { showDatePicker = true },
            )
            PauseButton(
                label = stringResource(R.string.focus_mode_25),
                onClick = { viewModel.activateFocusMode() },
            )
            if (!settings.strictMode) {
                PauseButton(
                    label = stringResource(R.string.emergency_bypass),
                    onClick = { viewModel.emergencyBypass() },
                )
            }
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
                    viewModel.pauseForTargets(
                        PauseType.untilDatetime,
                        profileIdsForPause(),
                        untilMs = cal.timeInMillis,
                    )
                    showTimePicker = false
                }) { Text(stringResource(R.string.pause)) }
            },
            text = { TimePicker(state = timeState) },
        )
    }
}

@Composable
private fun PauseButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(label) }
}
