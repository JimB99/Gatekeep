package com.gatekeep.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gatekeep.app.R
import com.gatekeep.app.util.EnforcementLog
import com.gatekeep.app.util.PermissionHelper

@Composable
fun PermissionBanner(
    state: PermissionState,
    onEnableEnforcement: (() -> Unit)? = null,
) {
    if (!state.showBanner) return
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (!state.enforcementEnabled) {
                Text(
                    stringResource(R.string.enforcement_disabled_title),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.enforcement_disabled_body),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (onEnableEnforcement != null) {
                    Button(
                        onClick = onEnableEnforcement,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text(stringResource(R.string.turn_on_enforcement)) }
                }
            }
            if (!state.allGranted || state.lastError != null) {
                if (!state.enforcementEnabled) {
                    Text(
                        stringResource(R.string.permissions_needed),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Text(
                        if (state.lastError != null) {
                            stringResource(R.string.enforcement_issue)
                        } else {
                            stringResource(R.string.permissions_needed)
                        },
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                }
                if (!state.usageGranted) Text(stringResource(R.string.usage_not_granted))
                if (!state.accessibilityGranted) Text(stringResource(R.string.accessibility_not_enabled))
                if (!state.overlayGranted) Text(stringResource(R.string.overlay_not_granted))
                state.lastError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (!state.accessibilityGranted) {
                    Button(
                        onClick = { context.startActivity(PermissionHelper.accessibilityIntent()) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text(stringResource(R.string.open_accessibility_settings)) }
                }
            }
        }
    }
}

data class PermissionState(
    val usageGranted: Boolean,
    val accessibilityGranted: Boolean,
    val overlayGranted: Boolean,
    val lastError: String?,
    val enforcementEnabled: Boolean = true,
) {
    val allGranted: Boolean get() = usageGranted && accessibilityGranted && overlayGranted
    val showBanner: Boolean get() = !enforcementEnabled || !allGranted || lastError != null
}

fun buildPermissionState(
    context: android.content.Context,
    enforcementLog: EnforcementLog,
    enforcementEnabled: Boolean = true,
): PermissionState =
    PermissionState(
        usageGranted = PermissionHelper.hasUsageStatsPermission(context),
        accessibilityGranted = PermissionHelper.isAccessibilityEnabled(context),
        overlayGranted = PermissionHelper.hasOverlayPermission(context),
        lastError = enforcementLog.getLastError(),
        enforcementEnabled = enforcementEnabled,
    )
