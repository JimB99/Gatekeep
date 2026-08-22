package com.gatekeep.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gatekeep.app.ui.viewmodel.StatsViewModel
import com.gatekeep.domain.StreakCalculator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val profile by viewModel.activeProfile.collectAsState()
    var overrideCount by remember { mutableIntStateOf(0) }
    val streak = remember { StreakCalculator.calculate(listOf(true, true, false, true, true)) }
    val sampleUsage = remember { listOf(45, 62, 38, 71, 55, 48, 33) }

    LaunchedEffect(profile?.id) {
        profile?.id?.let { overrideCount = viewModel.overrideCount(it) }
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
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Current streak: ${streak.currentStreakDays} days")
                    Text("Longest streak: ${streak.longestStreakDays} days")
                    Text("Override count: $overrideCount")
                }
            }
            Text("Daily usage (sample minutes)")
            sampleUsage.forEachIndexed { index, minutes ->
                Text("Day ${index + 1}: $minutes min")
                LinearProgressIndicator(
                    progress = { minutes / 120f },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
            }
        }
    }
}
