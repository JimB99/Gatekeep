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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
                title = { Text("Pause limits") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Scope")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = scopeIndex == 0, onClick = { scopeIndex = 0 }, label = { Text("All") })
                FilterChip(selected = scopeIndex == 1, onClick = { scopeIndex = 1 }, label = { Text("Active profile") })
            }
            PauseButton("Pause 5 minutes") { pauseQuick(viewModel, PauseType.fiveMin, profiles, scopeIndex) }
            PauseButton("Pause 15 minutes") { pauseQuick(viewModel, PauseType.fifteenMin, profiles, scopeIndex) }
            PauseButton("Pause 60 minutes") { pauseQuick(viewModel, PauseType.sixtyMin, profiles, scopeIndex) }
            PauseButton("Pause until date…") { showDatePicker = true }
            PauseButton("Focus mode (25 min)") { viewModel.activateFocusMode() }
            if (!settings.strictMode) {
                PauseButton("Emergency bypass (15 min)") { viewModel.emergencyBypass() }
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
                }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
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
                }) { Text("Pause") }
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
