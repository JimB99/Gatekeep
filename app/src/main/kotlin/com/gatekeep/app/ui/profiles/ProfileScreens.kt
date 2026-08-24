package com.gatekeep.app.ui.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gatekeep.app.ui.components.DurationPicker
import com.gatekeep.app.ui.components.PinGateDialog
import com.gatekeep.app.ui.viewmodel.ProfileViewModel
import com.gatekeep.app.util.PasswordHasher
import com.gatekeep.domain.model.FrictionMethod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDetailScreen(
    profileId: Long,
    onBack: () -> Unit,
    onEditApps: () -> Unit,
    onEditSchedule: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsState()
    val profile = profiles.find { it.id == profileId }
    val appPasswordHash by viewModel.appPasswordHash.collectAsState()
    var defaultFriction by remember(profile?.defaultFrictionMethod) {
        mutableStateOf(profile?.defaultFrictionMethod ?: FrictionMethod.math)
    }
    var dailyMs by remember(profile?.dailyLimitMs) { mutableLongStateOf(profile?.dailyLimitMs ?: 60 * 60_000L) }
    var sessionMs by remember(profile?.sessionLimitMs) { mutableLongStateOf(profile?.sessionLimitMs ?: 15 * 60_000L) }
    var breakMs by remember(profile?.breakDurationMs) { mutableLongStateOf(profile?.breakDurationMs ?: 5 * 60_000L) }
    var hourlyMs by remember(profile?.hourlyLimitMs) { mutableLongStateOf(profile?.hourlyLimitMs ?: 0L) }
    var weeklyMs by remember(profile?.weeklyLimitMs) { mutableLongStateOf(profile?.weeklyLimitMs ?: 0L) }
    var pinEnabled by remember(profile?.lockEnabled) { mutableStateOf(profile?.lockEnabled == true) }
    var pin by remember { mutableStateOf("") }
    var showDeactivatePinGate by remember { mutableStateOf(false) }

    if (showDeactivatePinGate && profile != null) {
        PinGateDialog(
            title = "Enter app PIN to change profile",
            passwordHash = appPasswordHash,
            onDismiss = { showDeactivatePinGate = false },
            onVerified = {
                showDeactivatePinGate = false
                viewModel.toggleProfileActive(profile.id, false)
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profile?.name ?: "Profile") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Active", fontWeight = FontWeight.Medium)
                    Switch(
                        checked = profile?.isActive == true,
                        onCheckedChange = { active ->
                            profile?.let {
                                if (!active && viewModel.requiresAppPin()) {
                                    showDeactivatePinGate = true
                                } else {
                                    viewModel.toggleProfileActive(it.id, active)
                                }
                            }
                        },
                    )
                }
            }
            Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onEditApps)) {
                Text("Manage apps", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Medium)
            }
            Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onEditSchedule)) {
                Text("Schedule windows", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Medium)
            }

            Text("Limits (apply to all apps in profile)", fontWeight = FontWeight.SemiBold)
            DurationPicker("Daily limit", dailyMs, onDurationChange = { dailyMs = it })
            DurationPicker("Session limit", sessionMs, minuteStep = 1, onDurationChange = { sessionMs = it })
            DurationPicker("Break duration", breakMs, onDurationChange = { breakMs = it })
            DurationPicker("Hourly limit (0 = off)", hourlyMs, onDurationChange = { hourlyMs = it })
            DurationPicker("Weekly limit (0 = off)", weeklyMs, minuteStep = 30, onDurationChange = { weeklyMs = it })
            Button(
                onClick = {
                    profile?.copy(
                        dailyLimitMs = dailyMs.takeIf { it > 0 },
                        sessionLimitMs = sessionMs.takeIf { it > 0 },
                        breakDurationMs = breakMs.takeIf { it > 0 },
                        hourlyLimitMs = hourlyMs.takeIf { it > 0 },
                        weeklyLimitMs = weeklyMs.takeIf { it > 0 },
                    )?.let { viewModel.updateProfile(it) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save limits") }

            Text("Default deterrent", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(FrictionMethod.math, FrictionMethod.waitOneMin, FrictionMethod.password).forEach { method ->
                    FilterChip(
                        selected = defaultFriction == method,
                        onClick = {
                            defaultFriction = method
                            profile?.copy(defaultFrictionMethod = method)?.let { viewModel.updateProfile(it) }
                        },
                        label = {
                            Text(
                                when (method) {
                                    FrictionMethod.math -> "Math"
                                    FrictionMethod.waitOneMin -> "Wait 1 min"
                                    FrictionMethod.password -> "PIN"
                                    else -> method.name
                                },
                            )
                        },
                    )
                }
            }

            Text("Profile PIN", fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = pinEnabled, onCheckedChange = { pinEnabled = it })
                Text("Require PIN to open apps")
            }
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it },
                label = { Text("Profile PIN") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    profile?.copy(
                        lockEnabled = pinEnabled,
                        passwordHash = if (pin.isNotBlank()) PasswordHasher.hash(pin) else profile.passwordHash,
                    )?.let { viewModel.updateProfile(it) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save profile PIN") }

            Card(modifier = Modifier.fillMaxWidth().clickable { viewModel.deleteProfile(profileId); onBack() }) {
                Text("Delete profile", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Medium)
            }
        }
    }
}
