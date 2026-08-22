package com.gatekeep.app.ui.pause

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gatekeep.app.ui.viewmodel.PauseViewModel
import com.gatekeep.domain.model.PauseType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PauseScreen(
    onBack: () -> Unit,
    viewModel: PauseViewModel = hiltViewModel(),
) {
    val profile by viewModel.activeProfile.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pause limits") },
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
            Text("Pause all limits temporarily")
            PauseButton("Pause 5 minutes") { viewModel.pause(PauseType.fiveMin, profile?.id, null) }
            PauseButton("Pause 15 minutes") { viewModel.pause(PauseType.fifteenMin, profile?.id, null) }
            PauseButton("Pause 60 minutes") { viewModel.pause(PauseType.sixtyMin, profile?.id, null) }
            PauseButton("Focus mode (25 min)") { viewModel.activateFocusMode() }
            PauseButton("Emergency bypass (15 min, 1×/week)") { viewModel.emergencyBypass() }
        }
    }
}

@Composable
private fun PauseButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}
