package com.gatekeep.app.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
    var dayOfWeek by remember { mutableStateOf("1") }
    var startHour by remember { mutableStateOf("18") }
    var endHour by remember { mutableStateOf("20") }
    var packageName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schedule windows") },
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
            Text("Multiple windows per day supported. Leave package empty for profile-wide.")
            OutlinedTextField(value = dayOfWeek, onValueChange = { dayOfWeek = it }, label = { Text("Day (0=Sun)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = startHour, onValueChange = { startHour = it }, label = { Text("Start hour") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = endHour, onValueChange = { endHour = it }, label = { Text("End hour") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = packageName, onValueChange = { packageName = it }, label = { Text("Package (optional)") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    viewModel.addWindow(
                        ScheduleWindow(
                            profileId = profileId,
                            packageName = packageName.ifBlank { null },
                            dayOfWeek = dayOfWeek.toIntOrNull() ?: 1,
                            startMinute = (startHour.toIntOrNull() ?: 0) * 60,
                            endMinute = (endHour.toIntOrNull() ?: 0) * 60,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add window") }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(windows) { window ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("${DAY_NAMES.getOrElse(window.dayOfWeek) { "?" }} ${window.startMinute / 60}:00 – ${window.endMinute / 60}:00")
                            window.packageName?.let { Text(it) } ?: Text("All apps in profile")
                            Button(onClick = { viewModel.deleteWindow(window.id) }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }
}
