package com.gatekeep.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gatekeep.app.R
import com.gatekeep.app.ui.GatekeepTestTags
import com.gatekeep.app.ui.permissions.PermissionsContent
import com.gatekeep.app.ui.permissions.rememberPermissionUiState
import com.gatekeep.app.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onFinishOnboarding: suspend () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()
    var enforcementLaunchHandled by remember { mutableStateOf(false) }
    LaunchedEffect(settings.onboardingComplete) {
        if (settings.onboardingComplete && !enforcementLaunchHandled) {
            enforcementLaunchHandled = true
            onComplete()
        }
    }

    val permissionState = rememberPermissionUiState()
    val snapshot = permissionState.snapshot

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(GatekeepTestTags.ONBOARDING_ROOT)
            .safeDrawingPadding()
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

        PermissionsContent(
            showReturnHint = false,
            permissionState = permissionState,
        )

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { scope.launch { onFinishOnboarding() } },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(GatekeepTestTags.ONBOARDING_GET_STARTED),
            enabled = snapshot.usageGranted && snapshot.accessibilityGranted && snapshot.overlayGranted,
        ) { Text(stringResource(R.string.get_started)) }

        Button(
            onClick = { scope.launch { onFinishOnboarding() } },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(GatekeepTestTags.ONBOARDING_SKIP),
        ) { Text(stringResource(R.string.skip_for_now)) }
    }
}
