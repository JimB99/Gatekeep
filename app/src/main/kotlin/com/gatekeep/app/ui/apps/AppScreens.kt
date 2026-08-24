package com.gatekeep.app.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gatekeep.app.ui.components.AppIcon
import com.gatekeep.app.ui.viewmodel.AppPickerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(
    profileId: Long,
    onBack: () -> Unit,
    viewModel: AppPickerViewModel = hiltViewModel(),
) {
    val installedApps by viewModel.installedApps.collectAsState()
    val monitored by viewModel.monitoredApps.collectAsState()
    val scheduleAllowed by viewModel.scheduleAllowedNow.collectAsState()
    var search by remember { mutableStateOf("") }

    LaunchedEffect(profileId) {
        viewModel.bindProfile(profileId)
    }

    val monitoredSet = monitored.map { it.packageName }.toSet()
    val filtered = installedApps.filter { app ->
        search.isBlank() || app.label.contains(search, true) || app.packageName.contains(search, true)
    }
    val selectedApps = filtered.filter { it.packageName in monitoredSet }
    val otherApps = filtered.filter { it.packageName !in monitoredSet }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select apps") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Search") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            ) {
                if (selectedApps.isNotEmpty()) {
                    item { Text("Selected", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp)) }
                    items(selectedApps, key = { "sel-${it.packageName}" }) { app ->
                        AppPickerRow(
                            app = app,
                            checked = true,
                            scheduleAllowed = scheduleAllowed[app.packageName] != false,
                            onToggle = { checked ->
                                viewModel.toggleApp(profileId, app, checked)
                            },
                        )
                    }
                }
                if (otherApps.isNotEmpty()) {
                    item { Text("All apps", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp)) }
                    items(otherApps, key = { "all-${it.packageName}" }) { app ->
                        AppPickerRow(
                            app = app,
                            checked = false,
                            scheduleAllowed = scheduleAllowed[app.packageName] != false,
                            onToggle = { checked ->
                                viewModel.toggleApp(profileId, app, checked)
                            },
                        )
                    }
                }
            }
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
            .clickable { onToggle(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onToggle)
        AppIcon(app.packageName, modifier = Modifier.padding(0.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.label, color = contentColor)
            Text(
                if (scheduleAllowed) app.packageName else "Not available right now",
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
            )
        }
    }
}
