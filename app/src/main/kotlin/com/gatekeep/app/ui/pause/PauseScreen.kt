package com.gatekeep.app.ui.pause

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableIntStateOf
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PauseScreen(
    onBack: () -> Unit,
    viewModel: PauseViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var scopeIndex by remember { mutableIntStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedDateMs by remember { mutableStateOf<Long?>(null) }

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GatekeepFilterChip(
                    selected = scopeIndex == 0,
                    onClick = { scopeIndex = 0 },
                    label = { Text(stringResource(R.string.scope_all)) },
                )
                GatekeepFilterChip(
                    selected = scopeIndex == 1,
                    onClick = { scopeIndex = 1 },
                    label = { Text(stringResource(R.string.scope_active_profile)) },
                )
            }
            PauseButton(stringResource(R.string.pause_5_min)) {
                pauseQuick(viewModel, PauseType.fiveMin, profiles, scopeIndex)
            }
            PauseButton(stringResource(R.string.pause_15_min)) {
                pauseQuick(viewModel, PauseType.fifteenMin, profiles, scopeIndex)
            }
            PauseButton(stringResource(R.string.pause_60_min)) {
                pauseQuick(viewModel, PauseType.sixtyMin, profiles, scopeIndex)
            }
            PauseButton(stringResource(R.string.pause_until_date)) { showDatePicker = true }
            PauseButton(stringResource(R.string.focus_mode_25)) { viewModel.activateFocusMode() }
            if (!settings.strictMode) {
                PauseButton(stringResource(R.string.emergency_bypass)) { viewModel.emergencyBypass() }
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
                    val profileId = if (scopeIndex == 1) profiles.firstOrNull { it.isActive }?.id else null
                    viewModel.pause(PauseType.untilDatetime, profileId, null, cal.timeInMillis)
                    showTimePicker = false
                }) { Text(stringResource(R.string.pause)) }
            },
            text = { TimePicker(state = timeState) },
        )
    }
}

private fun pauseQuick(
    viewModel: PauseViewModel,
    type: PauseType,
    profiles: List<com.gatekeep.domain.model.Profile>,
    scopeIndex: Int,
) {
    val profileId = when (scopeIndex) {
        1 -> profiles.firstOrNull { it.isActive }?.id
        else -> null
    }
    viewModel.pause(type, profileId, null)
}

@Composable
private fun PauseButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}
