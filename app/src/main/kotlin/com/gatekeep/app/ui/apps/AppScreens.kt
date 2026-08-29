package com.gatekeep.app.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gatekeep.app.R
import com.gatekeep.app.ui.components.AppIcon
import com.gatekeep.app.ui.components.SaveChangesButton
import com.gatekeep.app.ui.components.rememberUnsavedChangesGuard
import com.gatekeep.app.ui.viewmodel.AppPickerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(
    profileId: Long,
    onBack: () -> Unit,
    viewModel: AppPickerViewModel = hiltViewModel(),
) {
    val visibleApps by viewModel.visibleApps.collectAsState()
    val draftMonitored by viewModel.draftMonitoredPackages.collectAsState()
    val scheduleAllowed by viewModel.scheduleAllowedNow.collectAsState()
    val showSystemApps by viewModel.showSystemApps.collectAsState()
    val isDirty by viewModel.isDirty.collectAsState()
    var search by remember { mutableStateOf("") }

    LaunchedEffect(profileId) {
        viewModel.bindProfile(profileId)
    }

    fun saveChanges() {
        viewModel.commitChanges(profileId)
    }

    fun discardChanges() {
        viewModel.discardChanges()
    }

    val backGuard = rememberUnsavedChangesGuard(
        isDirty = isDirty,
        onNavigateBack = onBack,
        onSave = ::saveChanges,
        onDiscardChanges = ::discardChanges,
    )

    val filteredApps by remember {
        derivedStateOf {
            visibleApps.filter { app ->
                search.isBlank() ||
                    app.label.contains(search, true) ||
                    app.packageName.contains(search, true)
            }
        }
    }
    val selectedApps by remember {
        derivedStateOf { filteredApps.filter { it.packageName in draftMonitored } }
    }
    val otherApps by remember {
        derivedStateOf { filteredApps.filter { it.packageName !in draftMonitored } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.select_apps)) },
                navigationIcon = {
                    IconButton(onClick = backGuard::navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text(stringResource(R.string.search)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.show_system_apps))
                Switch(
                    checked = showSystemApps,
                    onCheckedChange = viewModel::setShowSystemApps,
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            ) {
                if (selectedApps.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.selected),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    items(selectedApps, key = { it.packageName }) { app ->
                        AppPickerRow(
                            app = app,
                            checked = true,
                            scheduleAllowed = scheduleAllowed[app.packageName] != false,
                            onToggle = { checked ->
                                viewModel.toggleApp(app.packageName, checked)
                            },
                        )
                    }
                }
                if (otherApps.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.all_apps),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    items(otherApps, key = { it.packageName }) { app ->
                        AppPickerRow(
                            app = app,
                            checked = false,
                            scheduleAllowed = scheduleAllowed[app.packageName] != false,
                            onToggle = { checked ->
                                viewModel.toggleApp(app.packageName, checked)
                            },
                        )
                    }
                }
            }
            SaveChangesButton(
                visible = isDirty,
                onClick = ::saveChanges,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun AppPickerRow(
    app: com.gatekeep.app.data.InstalledAppEntry,
    checked: Boolean,
    scheduleAllowed: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val contentColor = if (scheduleAllowed) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp, max = 56.dp)
            .clickable { onToggle(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onToggle)
        AppIcon(app.packageName, modifier = Modifier.size(32.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(app.label, color = contentColor, maxLines = 1)
            Text(
                if (scheduleAllowed) app.packageName else stringResource(R.string.not_available_now),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}
