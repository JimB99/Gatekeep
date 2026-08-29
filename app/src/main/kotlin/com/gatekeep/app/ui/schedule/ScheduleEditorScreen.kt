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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gatekeep.app.R
import com.gatekeep.app.ui.components.DayOfWeekSelector
import com.gatekeep.app.ui.components.SaveChangesButton
import com.gatekeep.app.ui.components.TimeOfDayPicker
import com.gatekeep.app.ui.components.rememberUnsavedChangesGuard
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
    val editorState by viewModel.editorState.collectAsState()
    val draftForm = editorState.draftForm
    val draftWindows = editorState.draftWindows.filter { !it.isProfileAutoSwitch }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val groupedWindows = remember(draftWindows) { ScheduleWindowGrouper.group(draftWindows) }
    val validation = remember(draftWindows, draftForm) {
        val invalidRange = draftForm.startMinute >= draftForm.endMinute
        val conflicts = if (invalidRange) {
            emptySet()
        } else {
            ScheduleConflictChecker.conflictingDays(
                existing = draftWindows,
                days = draftForm.selectedDays,
                startMinute = draftForm.startMinute,
                endMinute = draftForm.endMinute,
            )
        }
        ScheduleEditorValidation(
            invalidRange = invalidRange,
            selectedDays = draftForm.selectedDays,
            conflictingDays = conflicts,
        )
    }

    fun saveSchedule() {
        viewModel.commitSchedule(profileId)
    }

    fun discardChanges() {
        viewModel.discardChanges()
    }

    val backGuard = rememberUnsavedChangesGuard(
        isDirty = editorState.isDirty,
        onNavigateBack = onBack,
        onSave = ::saveSchedule,
        onDiscardChanges = ::discardChanges,
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.allowed_hours)) },
                navigationIcon = {
                    IconButton(onClick = backGuard::navigateBack) {
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
                DayOfWeekSelector(
                    selectedDays = draftForm.selectedDays,
                    onSelectionChange = { days ->
                        viewModel.updateForm { it.copy(selectedDays = days) }
                    },
                )
                TimeOfDayPicker(
                    stringResource(R.string.start_time),
                    draftForm.startMinute,
                    coarseStepMinutes = 60,
                    fineStepMinutes = 15,
                    onTimeChange = { minute ->
                        viewModel.updateForm { it.copy(startMinute = minute) }
                    },
                )
                TimeOfDayPicker(
                    stringResource(R.string.end_time),
                    draftForm.endMinute,
                    coarseStepMinutes = 60,
                    fineStepMinutes = 15,
                    onTimeChange = { minute ->
                        viewModel.updateForm { it.copy(endMinute = minute) }
                    },
                )
                Button(
                    onClick = {
                        when {
                            validation.invalidRange -> {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.schedule_invalid_time_range),
                                    )
                                }
                            }
                            validation.selectedDays.isEmpty() -> {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.schedule_select_days),
                                    )
                                }
                            }
                            validation.allConflict -> {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.schedule_already_exists),
                                    )
                                }
                            }
                            else -> {
                                val newWindows = validation.addableDays.map { day ->
                                    ScheduleWindow(
                                        profileId = profileId,
                                        packageName = null,
                                        dayOfWeek = day,
                                        startMinute = draftForm.startMinute,
                                        endMinute = draftForm.endMinute,
                                    )
                                }
                                viewModel.addDraftWindows(newWindows)
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
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !validation.invalidRange && validation.selectedDays.isNotEmpty(),
                ) { Text(stringResource(R.string.add_windows)) }
                SaveChangesButton(
                    visible = editorState.isDirty,
                    onClick = ::saveSchedule,
                    label = stringResource(R.string.save_schedule),
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(groupedWindows, key = { group ->
                    group.windowIds.firstOrNull() ?: group.days.hashCode()
                }) { group ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(formatGroupedDays(group.days))
                            Text(
                                "${formatMinute(group.startMinute)} – ${formatMinute(group.endMinute)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Button(onClick = { viewModel.removeDraftWindows(group.windowIds) }) {
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
    val ordered = days.sortedWith(compareBy { day -> if (day == 0) 7 else day })
    return ordered.map { day -> stringResource(dayNameRes(day)) }.joinToString(", ")
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
