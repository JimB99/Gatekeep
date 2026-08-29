package com.gatekeep.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.gatekeep.app.R
import com.gatekeep.app.ui.components.AppIcon
import com.gatekeep.app.ui.components.PermissionBanner
import com.gatekeep.app.ui.viewmodel.ProfilesHomeViewModel
import com.gatekeep.app.util.formatDurationMinutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesHomeScreen(
    onNavigateStats: () -> Unit,
    onNavigatePause: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateApps: (Long) -> Unit,
    onNavigateSchedule: (Long) -> Unit,
    onNavigateProfileDetail: (Long) -> Unit,
    viewModel: ProfilesHomeViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsState()
    val summaries by viewModel.summaries.collectAsState()
    val monitoredPackages by viewModel.monitoredPackages.collectAsState()
    val permissionState by viewModel.permissionState.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshAll()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onNavigateStats) {
                        Icon(Icons.Default.BarChart, stringResource(R.string.stats))
                    }
                    IconButton(onClick = onNavigatePause) {
                        Icon(Icons.Default.Pause, stringResource(R.string.pause))
                    }
                    IconButton(onClick = onNavigateSettings) {
                        Icon(Icons.Default.Settings, stringResource(R.string.settings))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, stringResource(R.string.new_profile))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PermissionBanner(
                    state = permissionState,
                    onEnableEnforcement = { viewModel.enableEnforcement() },
                )
            }
            if (profiles.isEmpty()) {
                item {
                    Text(stringResource(R.string.create_profile_hint))
                }
            }
            items(profiles) { profile ->
                val summary = summaries[profile.id]
                Card(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { onNavigateProfileDetail(profile.id) },
                        )
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        profile.name,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        stringResource(
                                            R.string.apps_count_usage_today,
                                            summary?.appCount ?: 0,
                                            formatDurationMinutes(summary?.totalUsageMs ?: 0),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    val packages = monitoredPackages[profile.id].orEmpty()
                                    if (packages.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier.padding(top = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            packages.take(5).forEach { pkg ->
                                                AppIcon(pkg, modifier = Modifier.size(24.dp))
                                            }
                                            if (packages.size > 5) {
                                                Text(
                                                    "+${packages.size - 5}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            }
                                        }
                                    }
                                }
                                Switch(
                                    checked = profile.isActive,
                                    onCheckedChange = { viewModel.toggleActive(profile.id, it) },
                                )
                            }
                            summary?.apps?.forEach { app ->
                                val progress = app.limitMs?.let {
                                    if (it > 0) (app.usageMs.toFloat() / it).coerceIn(0f, 1f) else 0f
                                } ?: 0f
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    Text(
                                        stringResource(
                                            R.string.usage_format,
                                            app.label,
                                            formatDurationMinutes(app.usageMs),
                                            formatDurationMinutes(app.limitMs),
                                        ),
                                    )
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        drawStopIndicator = {},
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TextButton(onClick = { onNavigateApps(profile.id) }) {
                                    Text(stringResource(R.string.apps))
                                }
                                TextButton(onClick = { onNavigateSchedule(profile.id) }) {
                                    Text(stringResource(R.string.allowed_hours))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(stringResource(R.string.new_profile)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.name)) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        viewModel.createProfile(newName)
                        newName = ""
                        showCreate = false
                    }
                }) { Text(stringResource(R.string.create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
