package com.gatekeep.app.ui.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gatekeep.app.R
import com.gatekeep.app.util.PermissionHelper

data class PermissionSnapshot(
    val usageGranted: Boolean,
    val accessibilityGranted: Boolean,
    val overlayGranted: Boolean,
    val batteryGranted: Boolean,
    val notificationsGranted: Boolean,
)

data class PermissionUiState(
    val snapshot: PermissionSnapshot,
    val requestNotifications: () -> Unit,
)

@Composable
fun rememberPermissionUiState(): PermissionUiState {
    val context = LocalContext.current
    var permissionRefreshKey by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionRefreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permissionRefreshKey++ }

    val snapshot = PermissionSnapshot(
        usageGranted = remember(permissionRefreshKey) {
            PermissionHelper.hasUsageStatsPermission(context)
        },
        accessibilityGranted = remember(permissionRefreshKey) {
            PermissionHelper.isAccessibilityEnabled(context)
        },
        overlayGranted = remember(permissionRefreshKey) {
            PermissionHelper.hasOverlayPermission(context)
        },
        batteryGranted = remember(permissionRefreshKey) {
            PermissionHelper.isIgnoringBatteryOptimizations(context)
        },
        notificationsGranted = remember(permissionRefreshKey) {
            PermissionHelper.hasNotificationPermission(context)
        },
    )

    return PermissionUiState(
        snapshot = snapshot,
        requestNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
    )
}

@Composable
fun PermissionsContent(
    modifier: Modifier = Modifier,
    showReturnHint: Boolean = false,
    permissionState: PermissionUiState = rememberPermissionUiState(),
) {
    val context = LocalContext.current
    val snapshot = permissionState.snapshot

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (showReturnHint) {
            Text(
                stringResource(R.string.onboarding_return_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        PermissionCard(
            title = stringResource(R.string.perm_usage_title),
            description = stringResource(R.string.perm_usage_desc),
            granted = snapshot.usageGranted,
            onGrant = { context.startActivity(PermissionHelper.usageStatsIntent()) },
        )
        PermissionCard(
            title = stringResource(R.string.perm_accessibility_title),
            description = stringResource(R.string.perm_accessibility_desc),
            granted = snapshot.accessibilityGranted,
            onGrant = { context.startActivity(PermissionHelper.accessibilityIntent(context)) },
        )
        PermissionCard(
            title = stringResource(R.string.perm_overlay_title),
            description = stringResource(R.string.perm_overlay_desc),
            granted = snapshot.overlayGranted,
            onGrant = { context.startActivity(PermissionHelper.overlayIntent(context)) },
        )
        PermissionCard(
            title = stringResource(R.string.perm_battery_title),
            description = stringResource(R.string.perm_battery_desc),
            granted = snapshot.batteryGranted,
            onGrant = { context.startActivity(PermissionHelper.batteryOptimizationIntent(context)) },
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionCard(
                title = stringResource(R.string.notifications),
                description = stringResource(R.string.perm_notifications_desc),
                granted = snapshot.notificationsGranted,
                onGrant = permissionState.requestNotifications,
                actionLabelWhenNotGranted = stringResource(R.string.allow_notifications),
            )
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit,
    actionLabelWhenNotGranted: String? = null,
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
            Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when {
                        granted -> stringResource(R.string.perm_manage)
                        actionLabelWhenNotGranted != null -> actionLabelWhenNotGranted
                        else -> stringResource(R.string.open_settings)
                    },
                )
            }
        }
    }
}
