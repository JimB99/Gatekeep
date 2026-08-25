package com.gatekeep.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gatekeep.app.ui.components.AppIcon
import com.gatekeep.app.ui.viewmodel.StatsViewModel
import com.gatekeep.app.util.formatDurationMs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val profiles by viewModel.activeProfiles.collectAsState()
    val weekly by viewModel.weeklyUsage.collectAsState()
    val streak by viewModel.streak.collectAsState()
    val appStats by viewModel.appStats.collectAsState()
    val overrideCount by viewModel.overrideCount.collectAsState()
    val profile = profiles.firstOrNull()

    LaunchedEffect(profile?.id) {
        profile?.let { viewModel.load(it.id, it.name) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Profile: ${profile?.name ?: "None active"}")
                        Text("Current streak: ${streak.currentStreakDays} days")
                        Text("Longest streak: ${streak.longestStreakDays} days")
                        Text("Overrides: $overrideCount")
                    }
                }
            }
            item { Text("Last 7 days") }
            item {
                weekly.forEach { day ->
                    Text("${day.dayLabel}: ${formatDurationMs(day.usageMs)}")
                    LinearProgressIndicator(
                        progress = { (day.usageMs / (2f * 3600_000)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                    )
                }
            }
            item { Text("Today by app") }
            items(appStats) { stat ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppIcon(stat.packageName, modifier = Modifier.height(40.dp))
                        Column {
                            Text(stat.label)
                            Text("${formatDurationMs(stat.usageMs)} / ${formatDurationMs(stat.limitMs)}")
                        }
                    }
                }
            }
        }
    }
}
