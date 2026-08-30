package com.gatekeep.app.ui.policy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gatekeep.app.R
import com.gatekeep.app.ui.components.DurationPicker
import com.gatekeep.app.ui.components.SaveChangesButton
import com.gatekeep.app.ui.components.rememberUnsavedChangesGuard
import com.gatekeep.app.ui.profiles.RuleNavRow
import com.gatekeep.app.ui.profiles.limitActionLabel
import com.gatekeep.app.ui.profiles.openActionLabel
import com.gatekeep.app.ui.profiles.sessionActionLabel
import com.gatekeep.app.ui.viewmodel.ProfileViewModel
import com.gatekeep.domain.CustomizeOverrides
import com.gatekeep.domain.LimitField
import com.gatekeep.domain.LimitHierarchy
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.OnSessionLimitAction
import com.gatekeep.domain.model.SchedulePolicyMode
import com.gatekeep.domain.model.SchedulePolicyOverrides
import com.gatekeep.domain.model.ScheduleSegment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolicyOverrideRulesHubScreen(
    profileId: Long,
    scope: PolicyOverrideScope,
    onBack: () -> Unit,
    onNavigateOpen: () -> Unit,
    onNavigateLimit: () -> Unit,
    onNavigateSession: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    LaunchedEffect(profileId) {
        viewModel.bindProfile(profileId)
    }

  val profiles by viewModel.profiles.collectAsState()
    val profile = profiles.find { it.id == profileId }
    val segments by viewModel.scheduleSegments.collectAsState()
    val overrides = remember(profile, segments, scope) {
        resolveOverrides(profile, segments, scope)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rules)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            RuleNavRow(
                title = stringResource(R.string.when_opening_app),
                subtitle = openActionLabel(overrides.onOpenAction ?: OnOpenAction.none),
                onClick = onNavigateOpen,
            )
            RuleNavRow(
                title = stringResource(R.string.when_limit_reached),
                subtitle = stringResource(R.string.when_limit_reached_subtitle),
                detail = limitActionLabel(overrides.onLimitAction ?: OnLimitAction.limitWithExtensions),
                onClick = onNavigateLimit,
            )
            RuleNavRow(
                title = stringResource(R.string.when_session_limit_reached),
                subtitle = sessionActionLabel(
                    overrides.onSessionLimitAction ?: OnSessionLimitAction.limitWithExtensions,
                ),
                onClick = onNavigateSession,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolicyOverrideLimitsScreen(
    profileId: Long,
    scope: PolicyOverrideScope,
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    LaunchedEffect(profileId) {
        viewModel.bindProfile(profileId)
    }

    val profiles by viewModel.profiles.collectAsState()
    val profile = profiles.find { it.id == profileId }
    val segments by viewModel.scheduleSegments.collectAsState()
    val rawBase = remember(profile, segments, scope) { resolveOverrides(profile, segments, scope) }
    val base = remember(profile, rawBase) {
        profile?.let { CustomizeOverrides.resolveForEditor(it, rawBase) } ?: rawBase
    }

    var weeklyMs by remember(scope) { mutableLongStateOf(base.weeklyLimitMs ?: 0L) }
    var dailyMs by remember(scope) { mutableLongStateOf(base.dailyLimitMs ?: 0L) }
    var hourlyMs by remember(scope) { mutableLongStateOf(base.hourlyLimitMs ?: 0L) }
    var sessionMs by remember(scope) { mutableLongStateOf(base.sessionLimitMs ?: 0L) }
    var savedWeekly by remember(scope) { mutableLongStateOf(weeklyMs) }
    var savedDaily by remember(scope) { mutableLongStateOf(dailyMs) }
    var savedHourly by remember(scope) { mutableLongStateOf(hourlyMs) }
    var savedSession by remember(scope) { mutableLongStateOf(sessionMs) }

    LaunchedEffect(profile?.id, scope, base) {
        weeklyMs = base.weeklyLimitMs ?: 0L
        dailyMs = base.dailyLimitMs ?: 0L
        hourlyMs = base.hourlyLimitMs ?: 0L
        sessionMs = base.sessionLimitMs ?: 0L
        savedWeekly = weeklyMs
        savedDaily = dailyMs
        savedHourly = hourlyMs
        savedSession = sessionMs
    }

    val isDirty = weeklyMs != savedWeekly || dailyMs != savedDaily ||
        hourlyMs != savedHourly || sessionMs != savedSession
    val hierarchyValidation = LimitHierarchy.validate(weeklyMs, dailyMs, hourlyMs, sessionMs)
    val hierarchyError = stringResource(R.string.limits_hierarchy_error)
    val hierarchyHint = stringResource(R.string.limits_hierarchy_hint)

    fun currentOverrides() = SchedulePolicyOverrides(
        dailyLimitMs = dailyMs,
        hourlyLimitMs = hourlyMs,
        weeklyLimitMs = weeklyMs,
        sessionLimitMs = sessionMs,
        onOpenAction = base.onOpenAction,
        onLimitAction = base.onLimitAction,
        onSessionLimitAction = base.onSessionLimitAction,
    )

    fun saveLimits() {
        if (!hierarchyValidation.valid) return
        val p = profile ?: return
        when (scope) {
            is PolicyOverrideScope.NoScheduleMatch -> {
                viewModel.saveProfile(
                    p.copy(
                        noScheduleMatchMode = SchedulePolicyMode.customize,
                        noScheduleMatchOverrides = currentOverrides(),
                    ),
                )
            }
            is PolicyOverrideScope.Segment -> {
                viewModel.updateSegmentOverrides(scope.segmentId) { overrides ->
                    overrides.copy(
                        dailyLimitMs = dailyMs,
                        hourlyLimitMs = hourlyMs,
                        weeklyLimitMs = weeklyMs,
                        sessionLimitMs = sessionMs,
                    )
                }
            }
        }
        savedWeekly = weeklyMs
        savedDaily = dailyMs
        savedHourly = hourlyMs
        savedSession = sessionMs
    }

    val backGuard = rememberUnsavedChangesGuard(
        isDirty = isDirty,
        onNavigateBack = onBack,
        onSave = ::saveLimits,
        onDiscardChanges = onBack,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.time_limits)) },
                navigationIcon = {
                    IconButton(onClick = backGuard::navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.customize_limits_off_hint),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
            Text(
                hierarchyHint,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DurationPicker(
                label = stringResource(R.string.weekly_limit_off),
                totalMs = weeklyMs,
                coarseStepMinutes = 60,
                fineStepMinutes = 15,
                isSet = weeklyMs > 0,
                isError = LimitField.Weekly in hierarchyValidation.invalidFields,
                onDurationChange = { weeklyMs = it },
            )
            DurationPicker(
                label = stringResource(R.string.daily_limit),
                totalMs = dailyMs,
                coarseStepMinutes = 60,
                fineStepMinutes = 15,
                isSet = dailyMs > 0,
                isError = LimitField.Daily in hierarchyValidation.invalidFields,
                onDurationChange = { dailyMs = it },
            )
            DurationPicker(
                label = stringResource(R.string.hourly_limit_off),
                totalMs = hourlyMs,
                coarseStepMinutes = 15,
                fineStepMinutes = 5,
                minutesOnly = true,
                isSet = hourlyMs > 0,
                isError = LimitField.Hourly in hierarchyValidation.invalidFields,
                onDurationChange = { hourlyMs = it },
            )
            DurationPicker(
                label = stringResource(R.string.session_limit),
                totalMs = sessionMs,
                coarseStepMinutes = 15,
                fineStepMinutes = 5,
                isSet = sessionMs > 0,
                isError = LimitField.Session in hierarchyValidation.invalidFields,
                onDurationChange = { sessionMs = it },
            )
            if (!hierarchyValidation.valid) {
                Text(
                    hierarchyError,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                )
            }
            SaveChangesButton(visible = isDirty, onClick = ::saveLimits)
        }
    }
}

@Composable
internal fun PolicySectionDivider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private fun resolveOverrides(
    profile: com.gatekeep.domain.model.Profile?,
    segments: List<ScheduleSegment>,
    scope: PolicyOverrideScope,
): SchedulePolicyOverrides = when (scope) {
    is PolicyOverrideScope.NoScheduleMatch -> profile?.noScheduleMatchOverrides ?: SchedulePolicyOverrides()
    is PolicyOverrideScope.Segment -> segments.find { it.id == scope.segmentId }?.overrides
        ?: SchedulePolicyOverrides()
}
