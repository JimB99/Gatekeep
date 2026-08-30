package com.gatekeep.app.ui.policy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gatekeep.app.R
import com.gatekeep.app.ui.components.DayOfWeekSelector
import com.gatekeep.app.ui.components.GatekeepFilterChip
import com.gatekeep.app.ui.components.SaveChangesButton
import com.gatekeep.app.ui.components.TimeOfDayPicker
import com.gatekeep.app.ui.components.rememberUnsavedChangesGuard
import com.gatekeep.app.ui.profiles.RuleNavRow
import com.gatekeep.app.ui.profiles.schedulePolicyModeLabel
import com.gatekeep.app.ui.schedule.formatScheduleTimeRange
import com.gatekeep.app.ui.viewmodel.ProfileViewModel
import com.gatekeep.domain.model.SchedulePolicyMode
import com.gatekeep.domain.model.SchedulePolicyOverrides
import com.gatekeep.domain.model.ScheduleSegment
import com.gatekeep.domain.model.ScheduleWindow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentEditorScreen(
    profileId: Long,
    segmentId: Long?,
    onBack: () -> Unit,
    onNavigateCustomizeLimits: (Long) -> Unit,
    onNavigateCustomizeRules: (Long) -> Unit,
    onSegmentCreated: (Long) -> Unit = {},
    onDelete: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val segments by viewModel.scheduleSegments.collectAsState()
    val windows by viewModel.scheduleWindows.collectAsState()
    var activeSegmentId by remember(segmentId) { mutableStateOf(segmentId) }
    val existing = activeSegmentId?.let { id -> segments.find { it.id == id } }
    val segmentWindows = windows.filter { it.segmentId == activeSegmentId && !it.isProfileAutoSwitch }

    var label by remember(activeSegmentId) { mutableStateOf(existing?.label.orEmpty()) }
    var mode by remember(activeSegmentId) { mutableStateOf(existing?.mode ?: SchedulePolicyMode.default) }
    var savedMode by remember(activeSegmentId) { mutableStateOf(mode) }
    var selectedDays by remember(activeSegmentId) {
        mutableStateOf(segmentWindows.map { it.dayOfWeek }.toSet().ifEmpty { (0..6).toSet() })
    }
    var startMinute by remember(activeSegmentId) {
        mutableStateOf(segmentWindows.firstOrNull()?.startMinute ?: 9 * 60)
    }
    var endMinute by remember(activeSegmentId) {
        mutableStateOf(segmentWindows.firstOrNull()?.endMinute ?: 17 * 60)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isDirty by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(existing, segmentWindows, activeSegmentId) {
        if (isDirty) return@LaunchedEffect
        existing?.let { seg ->
            label = seg.label.orEmpty()
            mode = seg.mode
            savedMode = seg.mode
        }
        if (segmentWindows.isNotEmpty()) {
            selectedDays = segmentWindows.map { it.dayOfWeek }.toSet()
            startMinute = segmentWindows.first().startMinute
            endMinute = segmentWindows.first().endMinute
        }
    }

    fun buildOverrides(): SchedulePolicyOverrides =
        existing?.overrides ?: SchedulePolicyOverrides()

    fun save() {
        scope.launch {
            if (startMinute == endMinute) {
                snackbarHostState.showSnackbar(context.getString(R.string.schedule_invalid_time_range))
                return@launch
            }
            if (selectedDays.isEmpty()) {
                snackbarHostState.showSnackbar(context.getString(R.string.schedule_select_days))
                return@launch
            }
            val segment = ScheduleSegment(
                id = activeSegmentId ?: 0,
                profileId = profileId,
                label = label.trim().takeIf { it.isNotBlank() },
                isActive = existing?.isActive ?: true,
                mode = mode,
                sortOrder = existing?.sortOrder ?: segments.size,
                overrides = if (mode == SchedulePolicyMode.customize) buildOverrides() else SchedulePolicyOverrides(),
            )
            val newWindows = selectedDays.map { day ->
                ScheduleWindow(
                    profileId = profileId,
                    segmentId = activeSegmentId,
                    dayOfWeek = day,
                    startMinute = startMinute,
                    endMinute = endMinute,
                )
            }
            val savedId = viewModel.saveSegmentWithWindows(segment, newWindows)
            if (activeSegmentId == null) {
                activeSegmentId = savedId
                onSegmentCreated(savedId)
            }
            isDirty = false
            savedMode = mode
            snackbarHostState.showSnackbar(context.getString(R.string.saved))
        }
    }

    val backGuard = rememberUnsavedChangesGuard(
        isDirty = isDirty,
        onNavigateBack = onBack,
        onSave = ::save,
        onDiscardChanges = onBack,
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (activeSegmentId == null) {
                            stringResource(R.string.add_schedule)
                        } else {
                            stringResource(R.string.edit_schedule)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = backGuard::navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (activeSegmentId != null) {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_schedule)) },
                                onClick = {
                                    showMenu = false
                                    showDeleteDialog = true
                                },
                            )
                        }
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
            OutlinedTextField(
                value = label,
                onValueChange = { label = it; isDirty = true },
                label = { Text(stringResource(R.string.schedule_label_optional)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.schedule_hint), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SchedulePolicyMode.entries.forEach { entry ->
                    GatekeepFilterChip(
                        selected = mode == entry,
                        onClick = { mode = entry; isDirty = true },
                        label = { Text(schedulePolicyModeLabel(entry)) },
                    )
                }
            }
            Text(stringResource(R.string.allowed_days))
            DayOfWeekSelector(
                selectedDays = selectedDays,
                onSelectionChange = { selectedDays = it; isDirty = true },
            )
            TimeOfDayPicker(
                stringResource(R.string.start_time),
                startMinute,
                coarseStepMinutes = 60,
                fineStepMinutes = 15,
                onTimeChange = { startMinute = it; isDirty = true },
            )
            TimeOfDayPicker(
                stringResource(R.string.end_time),
                endMinute,
                coarseStepMinutes = 60,
                fineStepMinutes = 15,
                onTimeChange = { endMinute = it; isDirty = true },
            )
            Text(
                formatScheduleTimeRange(startMinute, endMinute),
                style = MaterialTheme.typography.bodySmall,
            )
            if (mode == SchedulePolicyMode.customize && activeSegmentId != null) {
                PolicySectionDivider()
                RuleNavRow(
                    title = stringResource(R.string.time_limits),
                    subtitle = stringResource(R.string.customize_limits_subtitle),
                    onClick = { onNavigateCustomizeLimits(activeSegmentId!!) },
                )
                RuleNavRow(
                    title = stringResource(R.string.rules),
                    subtitle = stringResource(R.string.customize_rules_subtitle),
                    onClick = { onNavigateCustomizeRules(activeSegmentId!!) },
                )
            }
            SaveChangesButton(visible = isDirty || activeSegmentId == null, onClick = ::save)
        }
    }

    if (showDeleteDialog && activeSegmentId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_schedule_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.delete_schedule_message,
                        label.ifBlank { stringResource(R.string.edit_schedule) },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSegment(activeSegmentId!!)
                    showDeleteDialog = false
                    onDelete()
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
