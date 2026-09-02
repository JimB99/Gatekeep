@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gatekeep.app.ui.policy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gatekeep.app.R
import com.gatekeep.app.ui.components.GatekeepFilterChip
import com.gatekeep.app.ui.components.GatekeepFilterChipLabel
import com.gatekeep.app.ui.components.GatekeepFilterChipRow
import com.gatekeep.app.ui.components.SaveChangesButton
import com.gatekeep.app.ui.components.rememberUnsavedChangesGuard
import com.gatekeep.app.ui.profiles.RuleNavRow
import com.gatekeep.app.ui.profiles.limitActionLabel
import com.gatekeep.app.ui.profiles.openActionLabel
import com.gatekeep.app.ui.profiles.profileLimitsSubtitle
import com.gatekeep.app.ui.profiles.schedulePolicyModeChipLabel
import com.gatekeep.app.ui.profiles.schedulePolicyModeLabel
import com.gatekeep.app.ui.profiles.sessionActionLabel
import com.gatekeep.app.ui.schedule.formatGroupedDays
import com.gatekeep.app.ui.schedule.formatScheduleTimeRange
import com.gatekeep.app.ui.viewmodel.ProfileViewModel
import com.gatekeep.domain.PolicyTimelineResolver
import com.gatekeep.domain.CustomizeOverrides
import com.gatekeep.domain.ScheduleWindowGrouper
import com.gatekeep.domain.model.Profile
import com.gatekeep.domain.model.SchedulePolicyMode
import com.gatekeep.domain.model.ScheduleSegment
import com.gatekeep.domain.model.ScheduleWindow
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePolicyScreen(
    profileId: Long,
    onBack: () -> Unit,
    initialTab: Int = 0,
    onNavigateLimits: () -> Unit,
    onNavigateRulesOpen: () -> Unit,
    onNavigateRulesLimit: () -> Unit,
    onNavigateRulesSession: () -> Unit,
    onNavigateNoMatchLimits: () -> Unit,
    onNavigateNoMatchRules: () -> Unit,
    onNavigateSegmentEditor: (Long?, Int) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsState()
    val profile = profiles.find { it.id == profileId }
    val segments by viewModel.scheduleSegments.collectAsState()
    val windows by viewModel.scheduleWindows.collectAsState()
    val effectivePolicy by viewModel.effectivePolicy.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var defaultTabDirty by remember { mutableStateOf(false) }
    var defaultTabSave by remember { mutableStateOf<suspend () -> Unit>({}) }
    var defaultTabDiscard by remember { mutableStateOf<() -> Unit>({}) }

    LaunchedEffect(initialTab) {
        selectedTab = initialTab
    }

    LaunchedEffect(profileId) {
        viewModel.bindProfile(profileId)
        viewModel.refreshEffectivePolicy(profileId)
    }

    val backGuard = rememberUnsavedChangesGuard(
        isDirty = selectedTab == 0 && defaultTabDirty,
        onNavigateBack = onBack,
        onSave = { defaultTabSave() },
        onDiscardChanges = { defaultTabDiscard() },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.policy)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedTab == 0 && defaultTabDirty) {
                            backGuard.navigateBack()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            stringResource(R.string.policy_default_tab),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            stringResource(R.string.policy_schedules_tab),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Text(
                            stringResource(R.string.policy_week_tab),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
            when (selectedTab) {
                0 -> PolicyDefaultTab(
                    profile = profile,
                    effectivePolicy = effectivePolicy,
                    onNavigateLimits = onNavigateLimits,
                    onNavigateRulesOpen = onNavigateRulesOpen,
                    onNavigateRulesLimit = onNavigateRulesLimit,
                    onNavigateRulesSession = onNavigateRulesSession,
                    onNavigateNoMatchLimits = onNavigateNoMatchLimits,
                    onNavigateNoMatchRules = onNavigateNoMatchRules,
                    onSaveProfile = { viewModel.saveProfileAwait(it) },
                    onDirtyChange = { defaultTabDirty = it },
                    onSetupHandlers = { save, discard ->
                        defaultTabSave = save
                        defaultTabDiscard = discard
                    },
                )
                1 -> PolicySchedulesTab(
                    segments = segments,
                    windows = windows,
                    profile = profile,
                    onAddSchedule = { onNavigateSegmentEditor(null, 1) },
                    onEditSegment = { onNavigateSegmentEditor(it, 1) },
                    onToggleActive = { id, active -> viewModel.toggleSegmentActive(id, active) },
                    onDuplicate = { viewModel.duplicateSegment(it) },
                    onDelete = { viewModel.deleteSegment(it) },
                )
                else -> PolicyWeekTab(
                    segments = segments,
                    windows = windows,
                    locale = Locale.forLanguageTag(appSettings.languageTag.ifBlank { "en" }),
                    onSegmentSelected = { onNavigateSegmentEditor(it, 2) },
                )
            }
        }
    }
}

@Composable
private fun PolicyDefaultTab(
    profile: Profile?,
    effectivePolicy: PolicyTimelineResolver.EffectivePolicySnapshot?,
    onNavigateLimits: () -> Unit,
    onNavigateRulesOpen: () -> Unit,
    onNavigateRulesLimit: () -> Unit,
    onNavigateRulesSession: () -> Unit,
    onNavigateNoMatchLimits: () -> Unit,
    onNavigateNoMatchRules: () -> Unit,
    onSaveProfile: suspend (Profile) -> Unit,
    onDirtyChange: (Boolean) -> Unit,
    onSetupHandlers: (save: suspend () -> Unit, discard: () -> Unit) -> Unit = { _, _ -> },
) {
    val noMatchSaveScope = rememberCoroutineScope()
    var noMatchMode by remember(profile?.id) {
        mutableStateOf(profile?.noScheduleMatchMode ?: SchedulePolicyMode.default)
    }
    var savedNoMatchMode by remember(profile?.id) {
        mutableStateOf(profile?.noScheduleMatchMode ?: SchedulePolicyMode.default)
    }

    LaunchedEffect(profile?.noScheduleMatchMode) {
        profile?.noScheduleMatchMode?.let { mode ->
            if (noMatchMode == savedNoMatchMode) {
                noMatchMode = mode
                savedNoMatchMode = mode
            }
        }
    }

    val isDirty = noMatchMode != savedNoMatchMode

    LaunchedEffect(isDirty) {
        onDirtyChange(isDirty)
    }

    suspend fun saveNoMatch() {
        profile?.let { p ->
            val overrides = if (noMatchMode == SchedulePolicyMode.customize &&
                !CustomizeOverrides.hasAnyLimitValue(p.noScheduleMatchOverrides)
            ) {
                CustomizeOverrides.fullFromProfile(p)
            } else {
                p.noScheduleMatchOverrides
            }
            onSaveProfile(
                p.copy(
                    noScheduleMatchMode = noMatchMode,
                    noScheduleMatchOverrides = overrides,
                ),
            )
            savedNoMatchMode = noMatchMode
        }
    }

    fun discardNoMatch() {
        noMatchMode = savedNoMatchMode
    }

    SideEffect {
        onSetupHandlers({ saveNoMatch() }, ::discardNoMatch)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        profile?.let { p ->
            effectivePolicy?.let { snapshot ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.effective_policy_now),
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            schedulePolicyModeLabel(SchedulePolicyMode.valueOf(snapshot.modeLabel)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        snapshot.activeSegmentLabel?.let { label ->
                            Text(label, style = MaterialTheme.typography.bodySmall)
                        }
                        snapshot.nextChangeLabel?.let { next ->
                            Text(next, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable(onClick = onNavigateLimits),
            ) {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.time_limits), fontWeight = FontWeight.Medium)
                    },
                    supportingContent = { Text(profileLimitsSubtitle(p)) },
                )
            }
            PolicySectionDivider()
            Text(
                stringResource(R.string.rules),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            RuleNavRow(
                title = stringResource(R.string.when_opening_app),
                subtitle = openActionLabel(p.onOpenAction),
                onClick = onNavigateRulesOpen,
            )
            RuleNavRow(
                title = stringResource(R.string.when_limit_reached),
                subtitle = stringResource(R.string.when_limit_reached_subtitle),
                detail = limitActionLabel(p.onLimitAction),
                onClick = onNavigateRulesLimit,
            )
            RuleNavRow(
                title = stringResource(R.string.when_session_limit_reached),
                subtitle = sessionActionLabel(p.onSessionLimitAction),
                onClick = onNavigateRulesSession,
            )
            PolicySectionDivider()
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.when_no_schedule_matches), fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.when_no_schedule_matches_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                GatekeepFilterChipRow {
                    SchedulePolicyMode.entries.forEach { mode ->
                        GatekeepFilterChip(
                            selected = noMatchMode == mode,
                            onClick = { noMatchMode = mode },
                            label = { GatekeepFilterChipLabel(schedulePolicyModeChipLabel(mode)) },
                        )
                    }
                }
                if (isDirty) {
                    SaveChangesButton(
                        visible = true,
                        onClick = { noMatchSaveScope.launch { saveNoMatch() } },
                    )
                }
                if (noMatchMode == SchedulePolicyMode.customize) {
                    RuleNavRow(
                        title = stringResource(R.string.time_limits),
                        subtitle = stringResource(R.string.customize_limits_subtitle),
                        onClick = onNavigateNoMatchLimits,
                    )
                    RuleNavRow(
                        title = stringResource(R.string.rules),
                        subtitle = stringResource(R.string.customize_rules_subtitle),
                        onClick = onNavigateNoMatchRules,
                    )
                }
            }
        }
    }
}

@Composable
private fun PolicySchedulesTab(
    segments: List<ScheduleSegment>,
    windows: List<ScheduleWindow>,
    profile: Profile?,
    onAddSchedule: () -> Unit,
    onEditSegment: (Long) -> Unit,
    onToggleActive: (Long, Boolean) -> Unit,
    onDuplicate: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var segmentToDelete by remember { mutableStateOf<ScheduleSegment?>(null) }
    val enforcementWindows = windows.filter { !it.isProfileAutoSwitch }
    val segmentWindows = segments.associate { segment ->
        segment.id to enforcementWindows.filter { it.segmentId == segment.id }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        profile?.let { p ->
            Text(
                stringResource(R.string.schedules_summary_on_default_tab) + " " +
                    schedulePolicyModeLabel(p.noScheduleMatchMode),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(segments.sortedBy { it.sortOrder }, key = { it.id }) { segment ->
                val segWindows = segmentWindows[segment.id].orEmpty()
                val grouped = ScheduleWindowGrouper.group(segWindows)
                val timeLabel = grouped.firstOrNull()?.let { group ->
                    "${formatGroupedDays(group.days)} · ${formatScheduleTimeRange(group.startMinute, group.endMinute)}"
                } ?: stringResource(R.string.no_schedules)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable { onEditSegment(segment.id) },
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    segment.label ?: timeLabel,
                                    fontWeight = FontWeight.Medium,
                                    color = if (segment.isActive) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    },
                                )
                                if (segment.label != null) {
                                    Text(timeLabel, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    schedulePolicyModeLabel(segment.mode),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            androidx.compose.material3.Switch(
                                checked = segment.isActive,
                                onCheckedChange = { onToggleActive(segment.id, it) },
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { onDuplicate(segment.id) }) {
                                Text(stringResource(R.string.duplicate_schedule))
                            }
                            TextButton(onClick = { segmentToDelete = segment }) {
                                Text(
                                    stringResource(R.string.delete_schedule),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
        TextButton(
            onClick = onAddSchedule,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.add_schedule))
        }
    }

    segmentToDelete?.let { segment ->
        val segWindows = segmentWindows[segment.id].orEmpty()
        val grouped = ScheduleWindowGrouper.group(segWindows)
        val timeLabel = grouped.firstOrNull()?.let { group ->
            "${formatGroupedDays(group.days)} · ${formatScheduleTimeRange(group.startMinute, group.endMinute)}"
        } ?: stringResource(R.string.no_schedules)
        val label = segment.label ?: timeLabel
        AlertDialog(
            onDismissRequest = { segmentToDelete = null },
            title = { Text(stringResource(R.string.delete_schedule_title)) },
            text = { Text(stringResource(R.string.delete_schedule_message, label)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(segment.id)
                    segmentToDelete = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { segmentToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun PolicyWeekTab(
    segments: List<ScheduleSegment>,
    windows: List<ScheduleWindow>,
    locale: Locale,
    onSegmentSelected: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        WeekTimelineView(
            segments = segments,
            windows = windows,
            locale = locale,
            onSegmentSelected = onSegmentSelected,
        )
    }
}
