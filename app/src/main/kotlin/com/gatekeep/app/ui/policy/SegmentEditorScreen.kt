@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gatekeep.app.ui.policy

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gatekeep.app.R
import com.gatekeep.app.ui.components.DayOfWeekSelector
import com.gatekeep.app.ui.components.GatekeepFilterChip
import com.gatekeep.app.ui.components.GatekeepFilterChipLabel
import com.gatekeep.app.ui.components.GatekeepFilterChipRow
import com.gatekeep.app.ui.components.SaveChangesButton
import com.gatekeep.app.ui.components.TimeOfDayPicker
import com.gatekeep.app.ui.components.rememberUnsavedChangesGuard
import com.gatekeep.app.ui.profiles.RuleNavRow
import com.gatekeep.app.ui.profiles.overrideLimitsSubtitle
import com.gatekeep.app.ui.profiles.overrideRulesSubtitle
import com.gatekeep.app.ui.profiles.schedulePolicyModeChipLabel
import com.gatekeep.app.ui.profiles.schedulePolicyModeLabel
import com.gatekeep.app.ui.schedule.formatScheduleTimeRange
import com.gatekeep.app.ui.viewmodel.ProfileViewModel
import com.gatekeep.domain.CustomizeOverrides
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
    LaunchedEffect(profileId) {
        viewModel.bindProfile(profileId)
    }

    val segments by viewModel.scheduleSegments.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val profile = profiles.find { it.id == profileId }
    val windows by viewModel.scheduleWindows.collectAsState()
    var activeSegmentId by remember(segmentId) { mutableStateOf(segmentId) }
    val existing = activeSegmentId?.let { id -> segments.find { it.id == id } }
    val segmentWindows = windows.filter { it.segmentId == activeSegmentId && !it.isProfileAutoSwitch }

    var label by remember(activeSegmentId) { mutableStateOf("") }
    var mode by remember(activeSegmentId) { mutableStateOf(SchedulePolicyMode.default) }
    var selectedDays by remember(activeSegmentId) { mutableStateOf((0..6).toSet()) }
    var startMinute by remember(activeSegmentId) { mutableIntStateOf(9 * 60) }
    var endMinute by remember(activeSegmentId) { mutableIntStateOf(17 * 60) }

    var savedLabel by remember(activeSegmentId) { mutableStateOf("") }
    var savedMode by remember(activeSegmentId) { mutableStateOf(SchedulePolicyMode.default) }
    var savedDays by remember(activeSegmentId) { mutableStateOf((0..6).toSet<Int>()) }
    var savedStart by remember(activeSegmentId) { mutableIntStateOf(9 * 60) }
    var savedEnd by remember(activeSegmentId) { mutableIntStateOf(17 * 60) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    fun applyLoadedState(
        seg: ScheduleSegment?,
        wins: List<ScheduleWindow>,
    ) {
        val loadedLabel = seg?.label.orEmpty()
        val loadedMode = seg?.mode ?: SchedulePolicyMode.default
        val loadedDays = wins.map { it.dayOfWeek }.toSet().ifEmpty { (0..6).toSet() }
        val loadedStart = wins.firstOrNull()?.startMinute ?: 9 * 60
        val loadedEnd = wins.firstOrNull()?.endMinute ?: 17 * 60
        label = loadedLabel
        mode = loadedMode
        selectedDays = loadedDays
        startMinute = loadedStart
        endMinute = loadedEnd
        savedLabel = loadedLabel
        savedMode = loadedMode
        savedDays = loadedDays
        savedStart = loadedStart
        savedEnd = loadedEnd
    }

    LaunchedEffect(activeSegmentId, existing, segmentWindows) {
        if (activeSegmentId == null) {
            applyLoadedState(null, emptyList())
            return@LaunchedEffect
        }
        val isDirty = label != savedLabel || mode != savedMode || selectedDays != savedDays ||
            startMinute != savedStart || endMinute != savedEnd
        if (isDirty) return@LaunchedEffect
        if (existing != null) {
            applyLoadedState(existing, segmentWindows)
        }
    }

    LaunchedEffect(existing?.mode) {
        if (existing?.mode == SchedulePolicyMode.customize && mode == SchedulePolicyMode.customize) {
            savedMode = SchedulePolicyMode.customize
        }
    }

    val isDirty = label != savedLabel || mode != savedMode || selectedDays != savedDays ||
        startMinute != savedStart || endMinute != savedEnd

    fun buildOverrides(): SchedulePolicyOverrides {
        val current = existing?.overrides ?: SchedulePolicyOverrides()
        return if (profile != null && !CustomizeOverrides.hasAnyLimitValue(current)) {
            CustomizeOverrides.resolveForEditor(profile, current)
        } else {
            current
        }
    }

    suspend fun persistSegment(showSavedMessage: Boolean = true): Boolean {
        if (startMinute == endMinute) {
            snackbarHostState.showSnackbar(context.getString(R.string.schedule_invalid_time_range))
            return false
        }
        if (selectedDays.isEmpty()) {
            snackbarHostState.showSnackbar(context.getString(R.string.schedule_select_days))
            return false
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
        savedLabel = label
        savedMode = mode
        savedDays = selectedDays
        savedStart = startMinute
        savedEnd = endMinute
        if (showSavedMessage) {
            snackbarHostState.showSnackbar(context.getString(R.string.saved))
        }
        return true
    }

    fun save() {
        scope.launch { persistSegment() }
    }

    fun navigateCustomize(action: (Long) -> Unit) {
        val id = activeSegmentId ?: return
        scope.launch {
            if (isDirty) {
                if (!persistSegment(showSavedMessage = false)) return@launch
            }
            action(id)
        }
    }

    fun discardChanges() {
        label = savedLabel
        mode = savedMode
        selectedDays = savedDays
        startMinute = savedStart
        endMinute = savedEnd
    }

    val backGuard = rememberUnsavedChangesGuard(
        isDirty = isDirty,
        onNavigateBack = onBack,
        onSave = { persistSegment() },
        onDiscardChanges = ::discardChanges,
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
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
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
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.schedule_label_optional)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.schedule_hint), style = MaterialTheme.typography.bodySmall)
            GatekeepFilterChipRow {
                SchedulePolicyMode.entries.forEach { entry ->
                    GatekeepFilterChip(
                        selected = mode == entry,
                        onClick = { mode = entry },
                        label = { GatekeepFilterChipLabel(schedulePolicyModeChipLabel(entry)) },
                    )
                }
            }
            Text(stringResource(R.string.allowed_days))
            DayOfWeekSelector(
                selectedDays = selectedDays,
                onSelectionChange = { selectedDays = it },
            )
            TimeOfDayPicker(
                stringResource(R.string.start_time),
                startMinute,
                coarseStepMinutes = 60,
                fineStepMinutes = 15,
                onTimeChange = { startMinute = it },
            )
            TimeOfDayPicker(
                stringResource(R.string.end_time),
                endMinute,
                coarseStepMinutes = 60,
                fineStepMinutes = 15,
                onTimeChange = { endMinute = it },
            )
            Text(
                formatScheduleTimeRange(startMinute, endMinute),
                style = MaterialTheme.typography.bodySmall,
            )
            if (mode == SchedulePolicyMode.customize && activeSegmentId != null) {
                val overrides = existing?.overrides ?: SchedulePolicyOverrides()
                PolicySectionDivider()
                RuleNavRow(
                    title = stringResource(R.string.time_limits),
                    subtitle = stringResource(R.string.customize_limits_subtitle),
                    detail = overrideLimitsSubtitle(overrides),
                    onClick = { navigateCustomize(onNavigateCustomizeLimits) },
                )
                RuleNavRow(
                    title = stringResource(R.string.rules),
                    subtitle = stringResource(R.string.customize_rules_subtitle),
                    detail = overrideRulesSubtitle(overrides),
                    onClick = { navigateCustomize(onNavigateCustomizeRules) },
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
