package com.gatekeep.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gatekeep.app.ui.viewmodel.SettingsViewModel
import com.gatekeep.app.util.PermissionHelper

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Welcome to Gatekeep", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Gatekeep helps you manage screen time with profiles, session limits, schedules, and friction unlocks.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "The Session HUD shows a timer bar at the bottom of apps (like Digital Wellbeing). " +
                "It cannot appear in the Recents screen — that requires system privileges.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PermissionCard(
            title = "Usage Access",
            description = "Required to track how long you use each app.",
            granted = PermissionHelper.hasUsageStatsPermission(context),
            onGrant = { context.startActivity(PermissionHelper.usageStatsIntent()) },
        )
        PermissionCard(
            title = "Accessibility Service",
            description = "Detects which app is in the foreground to enforce limits.",
            granted = PermissionHelper.isAccessibilityEnabled(context),
            onGrant = { context.startActivity(PermissionHelper.accessibilityIntent()) },
        )
        PermissionCard(
            title = "Display Over Apps",
            description = "Shows block screen and session timer HUD.",
            granted = PermissionHelper.hasOverlayPermission(context),
            onGrant = { context.startActivity(PermissionHelper.overlayIntent(context)) },
        )
        PermissionCard(
            title = "Battery Optimization",
            description = "Prevents the system from killing Gatekeep on some devices.",
            granted = PermissionHelper.isIgnoringBatteryOptimizations(context),
            onGrant = { context.startActivity(PermissionHelper.batteryOptimizationIntent(context)) },
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !PermissionHelper.hasNotificationPermission(context)) {
            Button(
                onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Allow notifications") }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                viewModel.completeOnboarding()
                onComplete()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = PermissionHelper.hasUsageStatsPermission(context) &&
                PermissionHelper.isAccessibilityEnabled(context) &&
                PermissionHelper.hasOverlayPermission(context),
        ) { Text("Get started") }

        Button(
            onClick = {
                viewModel.completeOnboarding()
                onComplete()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Skip for now") }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text(
                if (granted) "Granted" else "Not granted",
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            if (!granted) {
                Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Settings")
                }
            }
        }
    }
}
