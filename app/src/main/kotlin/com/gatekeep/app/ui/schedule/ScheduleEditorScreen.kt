package com.gatekeep.app.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.gatekeep.app.ui.components.DayOfWeekSelector
import com.gatekeep.app.ui.components.TimeOfDayPicker
import com.gatekeep.app.ui.viewmodel.ScheduleViewModel
import com.gatekeep.domain.model.ScheduleWindow

private val DAY_NAMES = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorScreen(
    profileId: Long,
    onBack: () -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val windows by viewModel.windows(profileId).collectAsState()
    var selectedDays by remember { mutableStateOf(setOf(0, 1, 2, 3, 4, 5, 6)) }
    var startMinute by remember { mutableIntStateOf(9 * 60) }
    var endMinute by remember { mutableIntStateOf(17 * 60) }
    var packageName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Allowed hours") },
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
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Apps in this profile can only be used during these times.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Allowed days")
                DayOfWeekSelector(selectedDays = selectedDays, onSelectionChange = { selectedDays = it })
                TimeOfDayPicker("Start time", startMinute, onTimeChange = { startMinute = it })
                TimeOfDayPicker("End time", endMinute, onTimeChange = { endMinute = it })
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("Package (optional, profile-wide if empty)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        selectedDays.forEach { day ->
                            viewModel.addWindow(
                                ScheduleWindow(
                                    profileId = profileId,
                                    packageName = packageName.ifBlank { null },
                                    dayOfWeek = day,
                                    startMinute = startMinute,
                                    endMinute = endMinute,
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add window(s)") }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(windows) { window ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "${DAY_NAMES.getOrElse(window.dayOfWeek) { "?" }} " +
                                    "${formatMinute(window.startMinute)} – ${formatMinute(window.endMinute)}",
                            )
                            window.packageName?.let { Text(it) } ?: Text("All apps in profile")
                            Button(onClick = { viewModel.deleteWindow(window.id) }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMinute(minute: Int): String =
    "%02d:%02d".format(minute / 60, minute % 60)
