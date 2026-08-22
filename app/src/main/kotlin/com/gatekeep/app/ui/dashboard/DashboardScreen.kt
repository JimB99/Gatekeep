package com.gatekeep.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gatekeep.app.ui.viewmodel.DashboardViewModel
import com.gatekeep.app.util.formatDurationMs
import com.gatekeep.app.util.minutesToMs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateProfiles: () -> Unit,
    onNavigateStats: () -> Unit,
    onNavigatePause: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateApps: (Long) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val profile by viewModel.activeProfile.collectAsState()
    val monitoredApps by viewModel.monitoredApps.collectAsState()
    val limits by viewModel.limits.collectAsState()

    LaunchedEffect(profile?.id) {
        profile?.id?.let { viewModel.loadForProfile(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gatekeep") },
                actions = {
                    IconButton(onClick = onNavigateProfiles) { Icon(Icons.Default.Person, "Profiles") }
                    IconButton(onClick = onNavigateStats) { Icon(Icons.Default.BarChart, "Stats") }
                    IconButton(onClick = onNavigatePause) { Icon(Icons.Default.Pause, "Pause") }
                    IconButton(onClick = onNavigateSettings) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth().clickable { profile?.id?.let(onNavigateApps) }) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Active profile", style = MaterialTheme.typography.labelMedium)
                        Text(
                            profile?.name ?: "None",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("${monitoredApps.size} monitored apps — tap to manage")
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickActionCard("Focus 25m", Icons.Default.Timer, onNavigatePause, Modifier.weight(1f))
                    QuickActionCard("Stats", Icons.Default.BarChart, onNavigateStats, Modifier.weight(1f))
                }
            }

            if (monitoredApps.isEmpty()) {
                item {
                    Text(
                        "No apps monitored yet. Tap the profile card to add apps.",
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }

            items(monitoredApps) { app ->
                val limit = limits.find { it.packageName == app.packageName }
                val dailyLimit = limit?.dailyLimitMs
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(app.label, fontWeight = FontWeight.Medium)
                            if (app.isWhitelistedEssential) {
                                Text("Essential", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (dailyLimit != null) {
                            Text("Daily limit: ${formatDurationMs(dailyLimit)}")
                            if (limit.sessionLimitMs != null) {
                                Text("Session: ${formatDurationMs(limit.sessionLimitMs)} / break ${formatDurationMs(limit.breakDurationMs)}")
                            }
                            LinearProgressIndicator(progress = { 0.3f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        } else {
                            Text("No limits set")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = label)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
