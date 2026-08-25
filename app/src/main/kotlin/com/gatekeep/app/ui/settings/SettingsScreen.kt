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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gatekeep.app.BuildConfig
import com.gatekeep.app.admin.GatekeepDeviceAdminReceiver
import com.gatekeep.app.ui.components.PinTextField
import com.gatekeep.app.ui.components.TimeOfDayPicker
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
    var quietStart by remember(settings.quietHoursStartMinute) {
        mutableIntStateOf(settings.quietHoursStartMinute ?: 22 * 60)
    }
    var quietEnd by remember(settings.quietHoursEndMinute) {
        mutableIntStateOf(settings.quietHoursEndMinute ?: 7 * 60)
    }

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
            Text("App lock", style = MaterialTheme.typography.titleSmall)
            SettingToggleWithHelp(
                label = "Require PIN to open Gatekeep",
                help = "Locks the app when you leave Gatekeep or reopen it.",
                checked = settings.appLockEnabled,
            ) { viewModel.update { s -> s.copy(appLockEnabled = it) } }
            PinTextField(
                value = pin,
                onValueChange = { pin = it },
                label = "App PIN",
            )
            androidx.compose.material3.Button(
                onClick = {
                    if (pin.isNotBlank()) {
                        viewModel.update { s ->
                            s.copy(
                                appPasswordHash = PasswordHasher.hash(pin),
                                appLockEnabled = true,
                            )
                        }
                        pin = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save app PIN") }

            Text("Notifications", style = MaterialTheme.typography.titleSmall)
            SettingToggleWithHelp(
                label = "Session timer",
                help = "Countdown while a monitored app is open. You can swipe this away.",
                checked = settings.showSessionTimerNotification,
            ) {
                viewModel.update { s -> s.copy(showSessionTimerNotification = it, hudEnabled = it) }
            }
            SettingToggleWithHelp(
                label = "Limit warnings",
                help = "Alert when approaching daily or session limits.",
                checked = settings.warningAlertsEnabled,
            ) {
                viewModel.update { s -> s.copy(warningAlertsEnabled = it) }
            }
            SettingToggleWithHelp(
                label = "Weekly report",
                help = "Summary notification (respects quiet hours).",
                checked = settings.weeklyReportEnabled,
            ) {
                viewModel.update { s -> s.copy(weeklyReportEnabled = it) }
            }
            Text("Quiet hours", style = MaterialTheme.typography.labelMedium)
            Text(
                "No notifications during the selected times.",
                style = MaterialTheme.typography.bodySmall,
            )
            TimeOfDayPicker("Quiet hours start", quietStart, onTimeChange = { quietStart = it })
            TimeOfDayPicker("Quiet hours end", quietEnd, onTimeChange = { quietEnd = it })
            androidx.compose.material3.Button(
                onClick = {
                    viewModel.update { s ->
                        s.copy(quietHoursStartMinute = quietStart, quietHoursEndMinute = quietEnd)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save quiet hours") }

            Text("Enforcement", style = MaterialTheme.typography.titleSmall)
            SettingToggleWithHelp(
                label = "Enforcement enabled",
                help = "Turn off all blocking and timers without uninstalling Gatekeep.",
                checked = settings.enforcementEnabled,
            ) {
                viewModel.update { s -> s.copy(enforcementEnabled = it) }
            }
            SettingToggleWithHelp(
                label = "Strict mode",
                help = "Requires app PIN for profile changes, disables emergency bypass, and prompts for device admin.",
                checked = settings.strictMode,
            ) { enabled ->
                if (enabled && !settings.deviceAdminEnabled) {
                    enableDeviceAdmin(context)
                }
                viewModel.update { s -> s.copy(strictMode = enabled) }
            }
            SettingToggle(
                label = "Device admin (uninstall deterrent)",
                checked = settings.deviceAdminEnabled,
            ) { enabled ->
                if (enabled) enableDeviceAdmin(context)
                viewModel.update { it.copy(deviceAdminEnabled = enabled) }
            }

            androidx.compose.material3.Button(onClick = onReplayOnboarding, modifier = Modifier.fillMaxWidth()) {
                Text("Replay onboarding")
            }

            Text(
                if (viewModel.lastEnforcementError() != null) {
                    "Last error: ${viewModel.lastEnforcementError()}"
                } else {
                    "No recent enforcement errors"
                },
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Text(label, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingToggleWithHelp(
    label: String,
    help: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingToggle(label = label, checked = checked, onCheckedChange = onCheckedChange)
        Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
