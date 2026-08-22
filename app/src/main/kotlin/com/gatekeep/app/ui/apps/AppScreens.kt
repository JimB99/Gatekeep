package com.gatekeep.app.ui.apps

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gatekeep.app.ui.viewmodel.AppPickerViewModel
import com.gatekeep.app.ui.viewmodel.LimitEditorViewModel
import com.gatekeep.app.util.minutesToMs
import com.gatekeep.domain.AppCategories
import com.gatekeep.domain.model.AppCategory
import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.MonitoredApp

data class InstalledApp(val packageName: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(
    profileId: Long,
    onBack: () -> Unit,
    onEditLimit: (String) -> Unit,
    viewModel: AppPickerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val monitored by viewModel.monitoredApps(profileId).collectAsState()
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<AppCategory?>(null) }
    var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0 }
            .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label }
    }

    val monitoredSet = monitored.map { it.packageName }.toSet()
    val filtered = installedApps.filter { app ->
        (search.isBlank() || app.label.contains(search, true) || app.packageName.contains(search, true)) &&
            (selectedCategory == null || AppCategories.categoryForPackage(app.packageName) == selectedCategory)
    }

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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = selectedCategory == null, onClick = { selectedCategory = null }, label = { Text("All") })
                AppCategory.entries.filter { it != AppCategory.other }.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                        label = { Text(cat.name) },
                    )
                }
            }
            LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                items(filtered) { app ->
                    val isMonitored = app.packageName in monitoredSet
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isMonitored,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        viewModel.addApp(
                                            MonitoredApp(
                                                profileId = profileId,
                                                packageName = app.packageName,
                                                label = app.label,
                                                category = AppCategories.categoryForPackage(app.packageName),
                                            ),
                                        )
                                    } else {
                                        viewModel.removeApp(profileId, app.packageName)
                                    }
                                },
                            )
                            Column(modifier = Modifier.weight(1f).clickable { if (isMonitored) onEditLimit(app.packageName) }) {
                                Text(app.label)
                                Text(app.packageName, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLimitEditorScreen(
    profileId: Long,
    packageName: String,
    onBack: () -> Unit,
    viewModel: LimitEditorViewModel = hiltViewModel(),
) {
    var dailyMin by remember { mutableStateOf("60") }
    var sessionMin by remember { mutableStateOf("15") }
    var breakMin by remember { mutableStateOf("5") }
    var hourlyMin by remember { mutableStateOf("") }
    var weeklyHours by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }
    var isEssential by remember { mutableStateOf(false) }

    LaunchedEffect(profileId, packageName) {
        val existing = viewModel.getLimit(profileId, packageName)
        if (existing != null) {
            dailyMin = ((existing.dailyLimitMs ?: 0) / 60_000).toString()
            sessionMin = ((existing.sessionLimitMs ?: 0) / 60_000).toString()
            breakMin = ((existing.breakDurationMs ?: 0) / 60_000).toString()
            hourlyMin = existing.hourlyLimitMs?.let { (it / 60_000).toString() } ?: ""
            weeklyHours = existing.weeklyLimitMs?.let { (it / 3_600_000).toString() } ?: ""
            enabled = existing.enabled
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit limits") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(packageName)
            LimitField("Daily limit (minutes)", dailyMin) { dailyMin = it }
            LimitField("Session limit (minutes)", sessionMin) { sessionMin = it }
            LimitField("Break duration (minutes)", breakMin) { breakMin = it }
            LimitField("Hourly limit (minutes, optional)", hourlyMin) { hourlyMin = it }
            LimitField("Weekly limit (hours, optional)", weeklyHours) { weeklyHours = it }
            androidx.compose.material3.Switch(
                checked = isEssential,
                onCheckedChange = { isEssential = it },
            )
            Text("Whitelist as essential (always allowed)")
            androidx.compose.material3.Button(
                onClick = {
                    viewModel.saveLimit(
                        AppLimit(
                            profileId = profileId,
                            packageName = packageName,
                            dailyLimitMs = dailyMin.toLongOrNull()?.let { minutesToMs(it.toInt()) },
                            sessionLimitMs = sessionMin.toLongOrNull()?.let { minutesToMs(it.toInt()) },
                            breakDurationMs = breakMin.toLongOrNull()?.let { minutesToMs(it.toInt()) },
                            hourlyLimitMs = hourlyMin.toLongOrNull()?.let { minutesToMs(it.toInt()) },
                            weeklyLimitMs = weeklyHours.toLongOrNull()?.let { it * 3_600_000L },
                            enabled = enabled,
                        ),
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}

@Composable
private fun LimitField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
}
