@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gatekeep.app.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gatekeep.app.R
import com.gatekeep.app.ui.components.AppIcon
import com.gatekeep.app.ui.components.GatekeepFilterChip
import com.gatekeep.app.ui.components.GatekeepFilterChipRow
import com.gatekeep.app.ui.pause.PauseResetBlock
import com.gatekeep.app.ui.viewmodel.ProfileViewModel
import com.gatekeep.app.ui.viewmodel.ProfileViewModel.CurrentUsageAppRow
import com.gatekeep.app.ui.viewmodel.ProfileViewModel.CurrentUsageLimitKind
import com.gatekeep.app.ui.viewmodel.ProfileViewModel.CurrentUsageLimitRow
import com.gatekeep.app.util.formatDurationMs
import kotlinx.coroutines.launch

private val CURRENT_USAGE_EXTEND_MINUTES = listOf(5, 15, 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileCurrentUsageScreen(
    profileId: Long,
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val monitoredPackages by viewModel.monitoredPackages.collectAsState()
    val currentUsage by viewModel.currentUsage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val appliedMsg = stringResource(R.string.extension_applied)
    val resetDoneMsg = stringResource(R.string.extension_reset_done)

    LaunchedEffect(profileId) {
        viewModel.bindProfile(profileId)
        viewModel.refreshCurrentUsage(profileId)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_current_usage)) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PauseResetBlock(
                onReset = {
                    scope.launch {
                        viewModel.resetExtensionsForProfileAwait(profileId)
                        snackbarHostState.showSnackbar(resetDoneMsg)
                    }
                },
                enabled = true,
            )

            val usage = currentUsage
            if (usage == null) {
                // Waiting for first refresh
            } else if (usage.isSharedPool) {
                SharedUsageSection(
                    title = stringResource(R.string.extension_in_app_profile_heading),
                    limits = usage.sharedLimits,
                    onExtendMinutes = { minutes ->
                        scope.launch {
                            viewModel.grantExtensionInAppAwait(profileId, monitoredPackages, minutes)
                            snackbarHostState.showSnackbar(appliedMsg)
                        }
                    },
                    onNoLimitToday = {
                        scope.launch {
                            viewModel.grantNoLimitTodayInAppAwait(profileId, monitoredPackages)
                            snackbarHostState.showSnackbar(appliedMsg)
                        }
                    },
                )
            } else {
                usage.perApp.forEach { appRow ->
                    PerAppUsageCard(
                        appRow = appRow,
                        onExtendMinutes = { minutes ->
                            scope.launch {
                                viewModel.grantExtensionInAppAwait(
                                    profileId,
                                    listOf(appRow.packageName),
                                    minutes,
                                )
                                snackbarHostState.showSnackbar(appliedMsg)
                            }
                        },
                        onNoLimitToday = {
                            scope.launch {
                                viewModel.grantNoLimitTodayInAppAwait(
                                    profileId,
                                    listOf(appRow.packageName),
                                )
                                snackbarHostState.showSnackbar(appliedMsg)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedUsageSection(
    title: String,
    limits: List<CurrentUsageLimitRow>,
    onExtendMinutes: (Int) -> Unit,
    onNoLimitToday: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, fontWeight = FontWeight.Medium)
            limits.forEach { row -> UsageLimitBar(row) }
            CurrentUsageExtensionActions(onExtendMinutes, onNoLimitToday)
        }
    }
}

@Composable
private fun PerAppUsageCard(
    appRow: CurrentUsageAppRow,
    onExtendMinutes: (Int) -> Unit,
    onNoLimitToday: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppIcon(appRow.packageName, modifier = Modifier.size(24.dp))
                Text(appRow.label, fontWeight = FontWeight.Medium)
            }
            appRow.limits.forEach { row -> UsageLimitBar(row) }
            CurrentUsageExtensionActions(onExtendMinutes, onNoLimitToday)
        }
    }
}

@Composable
private fun UsageLimitBar(row: CurrentUsageLimitRow) {
    val label = limitKindLabel(row.kind)
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        if (row.noLimitToday) {
            Text(
                stringResource(R.string.no_limit_today_short),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (row.effectiveLimitMs != null) {
            Text(
                "${formatDurationMs(row.usageMs)} / ${formatDurationMs(row.effectiveLimitMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val scale = row.effectiveLimitMs.coerceAtLeast(1L)
            LinearProgressIndicator(
                progress = { (row.usageMs.toFloat() / scale).coerceIn(0f, 1f) },
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                formatDurationMs(row.usageMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun limitKindLabel(kind: CurrentUsageLimitKind): String = when (kind) {
    CurrentUsageLimitKind.weekly -> stringResource(R.string.weekly)
    CurrentUsageLimitKind.daily -> stringResource(R.string.daily_limit)
    CurrentUsageLimitKind.hourly -> stringResource(R.string.limit_hourly_label)
    CurrentUsageLimitKind.session -> stringResource(R.string.session_limit)
}

@Composable
private fun CurrentUsageExtensionActions(
    onExtendMinutes: (Int) -> Unit,
    onNoLimitToday: () -> Unit,
) {
    GatekeepFilterChipRow {
        CURRENT_USAGE_EXTEND_MINUTES.forEach { minutes ->
            GatekeepFilterChip(
                selected = false,
                onClick = { onExtendMinutes(minutes) },
                label = {
                    Text(
                        if (minutes == 60) {
                            stringResource(R.string.extension_one_hour)
                        } else {
                            stringResource(R.string.extension_minutes_format, minutes)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
    GatekeepFilterChip(
        selected = false,
        onClick = onNoLimitToday,
        label = {
            Text(
                stringResource(R.string.no_limit_today_short),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}
