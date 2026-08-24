package com.gatekeep.app.ui.components

import android.content.Intent
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
import androidx.compose.ui.unit.dp
import com.gatekeep.app.util.EnforcementLog
import com.gatekeep.app.util.PermissionHelper

@Composable
fun PermissionBanner(state: PermissionState) {
    if (state.allGranted && state.lastError == null) return
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                if (state.lastError != null) "Enforcement issue detected" else "Permissions needed",
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
            if (!state.usageGranted) Text("Usage access not granted")
            if (!state.accessibilityGranted) Text("Accessibility service not enabled")
            if (!state.overlayGranted) Text("Display over other apps not granted")
            state.lastError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (!state.accessibilityGranted) {
                Button(
                    onClick = { context.startActivity(PermissionHelper.accessibilityIntent()) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("Open Accessibility Settings") }
            }
        }
    }
}

data class PermissionState(
    val usageGranted: Boolean,
    val accessibilityGranted: Boolean,
    val overlayGranted: Boolean,
    val lastError: String?,
) {
    val allGranted: Boolean get() = usageGranted && accessibilityGranted && overlayGranted
}

fun buildPermissionState(context: android.content.Context, enforcementLog: EnforcementLog): PermissionState =
    PermissionState(
        usageGranted = PermissionHelper.hasUsageStatsPermission(context),
        accessibilityGranted = PermissionHelper.isAccessibilityEnabled(context),
        overlayGranted = PermissionHelper.hasOverlayPermission(context),
        lastError = enforcementLog.getLastError(),
    )
