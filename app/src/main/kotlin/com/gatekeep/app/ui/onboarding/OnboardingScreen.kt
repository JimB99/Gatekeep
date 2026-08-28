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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gatekeep.app.R
import com.gatekeep.app.ui.viewmodel.SettingsViewModel
import com.gatekeep.app.util.PermissionHelper

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    viewModel.settings.collectAsState()

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
        Text(
            stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.welcome_hud_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PermissionCard(
            title = stringResource(R.string.perm_usage_title),
            description = stringResource(R.string.perm_usage_desc),
            granted = PermissionHelper.hasUsageStatsPermission(context),
            onGrant = { context.startActivity(PermissionHelper.usageStatsIntent()) },
        )
        PermissionCard(
            title = stringResource(R.string.perm_accessibility_title),
            description = stringResource(R.string.perm_accessibility_desc),
            granted = PermissionHelper.isAccessibilityEnabled(context),
            onGrant = { context.startActivity(PermissionHelper.accessibilityIntent()) },
        )
        PermissionCard(
            title = stringResource(R.string.perm_overlay_title),
            description = stringResource(R.string.perm_overlay_desc),
            granted = PermissionHelper.hasOverlayPermission(context),
            onGrant = { context.startActivity(PermissionHelper.overlayIntent(context)) },
        )
        PermissionCard(
            title = stringResource(R.string.perm_battery_title),
            description = stringResource(R.string.perm_battery_desc),
            granted = PermissionHelper.isIgnoringBatteryOptimizations(context),
            onGrant = { context.startActivity(PermissionHelper.batteryOptimizationIntent(context)) },
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionHelper.hasNotificationPermission(context)
        ) {
            Button(
                onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.allow_notifications)) }
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
        ) { Text(stringResource(R.string.get_started)) }

        Button(
            onClick = {
                viewModel.completeOnboarding()
                onComplete()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.skip_for_now)) }
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
                if (granted) stringResource(R.string.granted) else stringResource(R.string.not_granted),
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            if (!granted) {
                Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.open_settings))
                }
            }
        }
    }
}
