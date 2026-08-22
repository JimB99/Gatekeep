package com.gatekeep.app.ui.settings

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.gatekeep.app.admin.GatekeepDeviceAdminReceiver
import com.gatekeep.app.ui.viewmodel.SettingsViewModel
import com.gatekeep.app.util.PasswordHasher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onReplayOnboarding: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingToggle("Session HUD enabled", settings.hudEnabled) {
                viewModel.update { s -> s.copy(hudEnabled = it) }
            }
            SettingToggle("Strict mode", settings.strictMode) {
                viewModel.update { s -> s.copy(strictMode = it) }
            }
            SettingToggle("Enforcement enabled", settings.enforcementEnabled) {
                viewModel.update { s -> s.copy(enforcementEnabled = it) }
            }
            SettingToggle("App lock", settings.appLockEnabled) {
                viewModel.update { s -> s.copy(appLockEnabled = it) }
            }
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it },
                label = { Text("App PIN") },
                modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.material3.Button(
                onClick = {
                    if (pin.isNotBlank()) {
                        viewModel.update { s -> s.copy(appPasswordHash = PasswordHasher.hash(pin)) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save PIN") }

            SettingToggle("Device admin (uninstall deterrent)", settings.deviceAdminEnabled) { enabled ->
                if (enabled) enableDeviceAdmin(context) else viewModel.update { it.copy(deviceAdminEnabled = enabled) }
                viewModel.update { it.copy(deviceAdminEnabled = enabled) }
            }

            androidx.compose.material3.Button(onClick = onReplayOnboarding, modifier = Modifier.fillMaxWidth()) {
                Text("Replay onboarding")
            }

            Text(
                "Backup/restore: export profiles from the Profiles screen (JSON). " +
                    "Weekly report notification runs every Sunday via WorkManager.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun enableDeviceAdmin(context: Context) {
    val component = ComponentName(context, GatekeepDeviceAdminReceiver::class.java)
    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Optional uninstall deterrent during strict mode")
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
