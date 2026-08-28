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

import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier

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

    val snackbarHostState = remember { SnackbarHostState() }
    val groupedWindows = remember(windows) { ScheduleWindowGrouper.group(windows) }
    val validation = remember(windows, selectedDays, startMinute, endMinute) {
        val invalidRange = startMinute >= endMinute
        val conflicts = if (invalidRange) {
            emptySet()
        } else {
            ScheduleConflictChecker.conflictingDays(
                existing = windows,
                days = selectedDays,
                startMinute = startMinute,
                endMinute = endMinute,
            )
        }
        ScheduleEditorValidation(invalidRange, conflicts)
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

                        selectedDays.forEach { day ->

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

                } else if (validation.conflictingDays.isNotEmpty()) {

                    Text(

                        stringResource(R.string.schedule_conflict),

                        style = MaterialTheme.typography.bodySmall,

                        color = MaterialTheme.colorScheme.error,

                    )

                }

            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {

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

private fun dayName(day: Int): String = when (day) {

    0 -> stringResource(R.string.day_sun)

    1 -> stringResource(R.string.day_mon)

    2 -> stringResource(R.string.day_tue)

    3 -> stringResource(R.string.day_wed)

    4 -> stringResource(R.string.day_thu)

    5 -> stringResource(R.string.day_fri)

    6 -> stringResource(R.string.day_sat)

    else -> "?"

}



private fun formatMinute(minute: Int): String =

    "%02d:%02d".format(minute / 60, minute % 60)

private data class ScheduleEditorValidation(
    val invalidRange: Boolean,
    val conflictingDays: Set<Int>,
) {
    val canAdd: Boolean get() = !invalidRange && conflictingDays.isEmpty()
}


