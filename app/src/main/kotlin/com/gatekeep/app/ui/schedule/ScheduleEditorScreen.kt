package com.gatekeep.app.ui.schedule

import android.content.Context
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gatekeep.app.R
import com.gatekeep.app.ui.components.DayOfWeekSelector
import com.gatekeep.app.ui.components.TimeOfDayPicker
import com.gatekeep.app.ui.viewmodel.ScheduleViewModel
import com.gatekeep.domain.ScheduleConflictChecker
import com.gatekeep.domain.ScheduleWindowGrouper
import com.gatekeep.domain.model.ScheduleWindow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorScreen(
    profileId: Long,
    onBack: () -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    LaunchedEffect(profileId) {
        viewModel.bindProfile(profileId)
    }
    val windows by viewModel.windows.collectAsState()
    var selectedDays by remember { mutableStateOf((0..6).toSet()) }
    var startMinute by remember { mutableIntStateOf(9 * 60) }
    var endMinute by remember { mutableIntStateOf(17 * 60) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val userWindows = remember(windows) { windows.filter { !it.isProfileAutoSwitch } }
    val groupedWindows = remember(userWindows) { ScheduleWindowGrouper.group(userWindows) }
    val validation = remember(userWindows, selectedDays, startMinute, endMinute) {
        val invalidRange = startMinute >= endMinute
        val conflicts = if (invalidRange) {
            emptySet()
        } else {
            ScheduleConflictChecker.conflictingDays(
                existing = userWindows,
                days = selectedDays,
                startMinute = startMinute,
                endMinute = endMinute,
            )
        }
        ScheduleEditorValidation(
            invalidRange = invalidRange,
            selectedDays = selectedDays,
            conflictingDays = conflicts,
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.allowed_hours)) },
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
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.schedule_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(stringResource(R.string.allowed_days))
                DayOfWeekSelector(selectedDays = selectedDays, onSelectionChange = { selectedDays = it })
                TimeOfDayPicker(stringResource(R.string.start_time), startMinute, onTimeChange = { startMinute = it })
                TimeOfDayPicker(stringResource(R.string.end_time), endMinute, onTimeChange = { endMinute = it })
                Button(
                    onClick = {
                        if (!validation.canAdd) return@Button
                        validation.addableDays.forEach { day ->
                            viewModel.addWindow(
                                ScheduleWindow(
                                    profileId = profileId,
                                    packageName = null,
                                    dayOfWeek = day,
                                    startMinute = startMinute,
                                    endMinute = endMinute,
                                ),
                            )
                        }
                        if (validation.hasSkippedDays) {
                            val skippedNames = validation.conflictingDays
                                .sorted()
                                .joinToString(", ") { day -> dayName(context, day) }
                            val skippedMessage = context.getString(
                                R.string.schedule_days_skipped,
                                skippedNames,
                            )
                            scope.launch {
                                snackbarHostState.showSnackbar(message = skippedMessage)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = validation.canAdd,
                ) { Text(stringResource(R.string.add_windows)) }
                if (validation.invalidRange) {
                    Text(
                        stringResource(R.string.schedule_invalid_time_range),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (validation.allConflict) {
                    Text(
                        stringResource(R.string.schedule_conflict),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (validation.selectedDays.isEmpty()) {
                    Text(
                        stringResource(R.string.schedule_select_days),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(groupedWindows, key = { it.windowIds.first() }) { group ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(formatGroupedDays(group.days))
                            Text(
                                "${formatMinute(group.startMinute)} – ${formatMinute(group.endMinute)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Button(onClick = { viewModel.deleteWindows(group.windowIds) }) {
                                Text(stringResource(R.string.remove))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun formatGroupedDays(days: Set<Int>): String {
    val ordered = days.sorted().map { day -> dayName(day) }
    return ordered.joinToString(", ")
}

@Composable
private fun dayName(day: Int): String = stringResource(dayNameRes(day))

private fun dayNameRes(day: Int): Int = when (day) {
    0 -> R.string.day_sun
    1 -> R.string.day_mon
    2 -> R.string.day_tue
    3 -> R.string.day_wed
    4 -> R.string.day_thu
    5 -> R.string.day_fri
    6 -> R.string.day_sat
    else -> R.string.day_sun
}

private fun dayName(context: Context, day: Int): String =
    context.getString(dayNameRes(day))

private fun formatMinute(minute: Int): String =
    "%02d:%02d".format(minute / 60, minute % 60)

private data class ScheduleEditorValidation(
    val invalidRange: Boolean,
    val selectedDays: Set<Int>,
    val conflictingDays: Set<Int>,
) {
    val addableDays: Set<Int> get() = selectedDays - conflictingDays
    val canAdd: Boolean get() = !invalidRange && selectedDays.isNotEmpty() && addableDays.isNotEmpty()
    val hasSkippedDays: Boolean get() = conflictingDays.isNotEmpty() && addableDays.isNotEmpty()
    val allConflict: Boolean get() = !invalidRange && selectedDays.isNotEmpty() && addableDays.isEmpty()
}
