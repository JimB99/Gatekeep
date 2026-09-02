package com.gatekeep.app.ui.home

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.gatekeep.app.R
import com.gatekeep.app.data.ProfileUsageSummary
import com.gatekeep.app.ui.components.AppIcon
import com.gatekeep.app.ui.components.PermissionBanner
import com.gatekeep.app.ui.viewmodel.ProfilesHomeViewModel
import com.gatekeep.app.util.formatDurationMinutes
import com.gatekeep.domain.model.Profile
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesHomeScreen(
    onNavigateStats: () -> Unit,
    onNavigatePause: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateApps: (Long) -> Unit,
    onNavigatePolicy: (Long) -> Unit,
    onNavigateProfileDetail: (Long) -> Unit,
    viewModel: ProfilesHomeViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsState()
    val summaries by viewModel.summaries.collectAsState()
    val monitoredPackages by viewModel.monitoredPackages.collectAsState()
    val permissionState by viewModel.permissionState.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var dragging by remember { mutableStateOf(false) }
    val orderedProfiles = remember { mutableStateListOf<Profile>() }
    val latestOrdered = rememberUpdatedState(orderedProfiles.toList())
    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromId = from.key as? Long ?: return@rememberReorderableLazyListState
        val toId = to.key as? Long ?: return@rememberReorderableLazyListState
        val fromIndex = orderedProfiles.indexOfFirst { it.id == fromId }
        val toIndex = orderedProfiles.indexOfFirst { it.id == toId }
        if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return@rememberReorderableLazyListState
        val item = orderedProfiles.removeAt(fromIndex)
        orderedProfiles.add(toIndex, item)
    }

    LaunchedEffect(profiles) {
        if (!dragging) {
            orderedProfiles.clear()
            orderedProfiles.addAll(profiles)
        }
    }

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
            state = lazyListState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "permission-banner") {
                PermissionBanner(
                    state = permissionState,
                    onEnableEnforcement = { viewModel.enableEnforcement() },
                    onDismissError = { viewModel.clearEnforcementError() },
                )
            }
            if (orderedProfiles.isEmpty()) {
                item(key = "empty-hint") {
                    Text(stringResource(R.string.create_profile_hint))
                }
            }
            items(orderedProfiles, key = { it.id }) { profile ->
                ReorderableItem(reorderableLazyListState, key = profile.id) {
                    ProfileHomeCard(
                        profile = profile,
                        summary = summaries[profile.id],
                        packages = monitoredPackages[profile.id].orEmpty(),
                        onOpen = { onNavigateProfileDetail(profile.id) },
                        onToggleActive = { viewModel.toggleActive(profile.id, it) },
                        onNavigateApps = { onNavigateApps(profile.id) },
                        onNavigatePolicy = { onNavigatePolicy(profile.id) },
                        onDuplicate = { viewModel.duplicateProfile(profile.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .longPressDraggableHandle(
                                onDragStarted = { dragging = true },
                                onDragStopped = {
                                    dragging = false
                                    viewModel.reorderProfiles(latestOrdered.value.map { it.id })
                                },
                            ),
                    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileHomeCard(
    profile: Profile,
    summary: ProfileUsageSummary?,
    packages: List<String>,
    onOpen: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onNavigateApps: () -> Unit,
    onNavigatePolicy: () -> Unit,
    onDuplicate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        onClick = onOpen,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
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
                    onCheckedChange = onToggleActive,
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.duplicate_profile)) },
                            onClick = {
                                menuExpanded = false
                                onDuplicate()
                            },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onNavigateApps) {
                    Text(stringResource(R.string.apps))
                }
                TextButton(onClick = onNavigatePolicy) {
                    Text(stringResource(R.string.policy))
                }
            }
        }
    }
}
