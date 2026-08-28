package com.gatekeep.app.ui.settings



import android.app.admin.DevicePolicyManager

import android.content.ComponentName

import android.content.Context

import android.content.Intent

import androidx.compose.foundation.clickable

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

import androidx.compose.material3.ListItem

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Scaffold

import androidx.compose.material3.Text

import androidx.compose.material3.TopAppBar

import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.collectAsState

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableIntStateOf

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.res.stringResource

import androidx.compose.ui.unit.dp

import androidx.hilt.navigation.compose.hiltViewModel

import com.gatekeep.app.BuildConfig

import com.gatekeep.app.R

import com.gatekeep.app.admin.GatekeepDeviceAdminReceiver

import com.gatekeep.app.ui.viewmodel.SettingsViewModel

import com.gatekeep.app.util.LocaleController



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun SettingsHubScreen(

    onBack: () -> Unit,

    onNavigateSecurity: () -> Unit,

    onNavigateNotifications: () -> Unit,

    onNavigateEnforcement: () -> Unit,

    onNavigateAbout: () -> Unit,

    onNavigateLanguage: () -> Unit,

    onReplayOnboarding: () -> Unit,

    viewModel: SettingsViewModel = hiltViewModel(),

) {

    val settings by viewModel.settings.collectAsState()



    Scaffold(

        topBar = {

            TopAppBar(

                title = { Text(stringResource(R.string.settings)) },

                navigationIcon = {

                    IconButton(onClick = onBack) {

                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))

                    }

                },

            )

        },

    ) { padding ->

        Column(

            modifier = Modifier

                .fillMaxSize()

                .padding(padding)

                .verticalScroll(rememberScrollState()),

        ) {

            SettingsNavItem(

                title = stringResource(R.string.language),

                subtitle = "${LocaleController.flagForTag(settings.languageTag)} " +
                    LocaleController.nativeLabelForTag(settings.languageTag),

                onClick = onNavigateLanguage,

            )

            SettingsNavItem(

                title = stringResource(R.string.security),

                subtitle = if (settings.hasAppPin()) {

                    stringResource(R.string.pin_set)

                } else {

                    stringResource(R.string.no_pin)

                },

                onClick = onNavigateSecurity,

            )

            SettingsNavItem(

                title = stringResource(R.string.notifications),

                subtitle = buildString {

                    if (settings.showSessionTimerNotification) append(stringResource(R.string.timer))

                    if (settings.warningAlertsEnabled) {

                        if (isNotEmpty()) append(" · ")

                        append(stringResource(R.string.warnings))

                    }

                    if (settings.weeklyReportEnabled) {

                        if (isNotEmpty()) append(" · ")

                        append(stringResource(R.string.weekly))

                    }

                    if (isEmpty()) append(stringResource(R.string.off))

                },

                onClick = onNavigateNotifications,

            )

            SettingsNavItem(

                title = stringResource(R.string.enforcement),

                subtitle = if (settings.enforcementEnabled) {

                    stringResource(R.string.on)

                } else {

                    stringResource(R.string.off)

                },

                onClick = onNavigateEnforcement,

            )

            SettingsNavItem(

                title = stringResource(R.string.about),

                subtitle = stringResource(R.string.version_format, BuildConfig.VERSION_NAME),

                onClick = onNavigateAbout,

            )

            SettingsNavItem(

                title = stringResource(R.string.replay_onboarding),

                subtitle = stringResource(R.string.permissions_and_setup),

                onClick = onReplayOnboarding,

            )

        }

    }

}



@Composable

private fun SettingsNavItem(title: String, subtitle: String, onClick: () -> Unit) {

    ListItem(

        headlineContent = { Text(title) },

        supportingContent = { Text(subtitle) },

        modifier = Modifier

            .fillMaxWidth()

            .clickable(onClick = onClick),

    )

}



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun SecuritySettingsScreen(

    onBack: () -> Unit,

    viewModel: SettingsViewModel = hiltViewModel(),

) {

    val settings by viewModel.settings.collectAsState()

    val context = LocalContext.current



    SettingsDetailScaffold(title = stringResource(R.string.security), onBack = onBack) {

        SettingToggleWithHelp(

            label = stringResource(R.string.require_pin_to_open),

            help = if (settings.hasAppPin()) {

                stringResource(R.string.app_lock_help_set)

            } else {

                stringResource(R.string.app_lock_help_unset)

            },

            checked = settings.appLockEnabled,

            enabled = settings.hasAppPin(),

        ) { enabled ->

            if (enabled) {

                viewModel.update { s -> s.copy(appLockEnabled = true) }

            } else {

                viewModel.update { s -> s.copy(appLockEnabled = false) }

            }

        }

        AppLockSection(viewModel, settings)

        Text(

            stringResource(R.string.protection),

            style = MaterialTheme.typography.titleSmall,

            modifier = Modifier.padding(top = 16.dp),

        )

        SettingToggleWithHelp(

            label = stringResource(R.string.strict_mode),

            help = stringResource(R.string.strict_mode_help),

            checked = settings.strictMode,

        ) { enabled ->

            if (enabled && !settings.deviceAdminEnabled) {

                enableDeviceAdmin(context)

            }

            viewModel.update { s -> s.copy(strictMode = enabled) }

        }

        SettingToggle(

            label = stringResource(R.string.device_admin),

            checked = settings.deviceAdminEnabled,

        ) { enabled ->

            if (enabled) enableDeviceAdmin(context)

            viewModel.update { it.copy(deviceAdminEnabled = enabled) }

        }

    }

}



@Composable

private fun AppLockSection(

    viewModel: SettingsViewModel,

    settings: com.gatekeep.data.repository.AppSettings,

) {

    var pin by remember { mutableStateOf("") }

    var savedPin by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {

        val loaded = viewModel.loadAppPin().orEmpty()

        pin = loaded

        savedPin = loaded

    }

    val pinDirty = pin.trim() != savedPin.trim()

    fun savePin() {
        val trimmed = pin.trim()
        if (trimmed.isNotBlank()) {
            viewModel.saveAppPin(trimmed)
            viewModel.update { s ->
                s.copy(appPasswordHash = com.gatekeep.app.util.PasswordHasher.hash(trimmed))
            }
            savedPin = trimmed
        } else {
            viewModel.clearAppPin()
            viewModel.update { s ->
                s.copy(appPasswordHash = null, appLockEnabled = false)
            }
            savedPin = ""
        }
    }

    com.gatekeep.app.ui.components.PinTextField(
        value = pin,
        onValueChange = { pin = it },
        label = stringResource(R.string.app_pin),
    )

    if (pinDirty) {
        androidx.compose.material3.Button(
            onClick = { savePin() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.save_app_pin)) }
    }

}



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun NotificationSettingsScreen(

    onBack: () -> Unit,

    viewModel: SettingsViewModel = hiltViewModel(),

) {

    val settings by viewModel.settings.collectAsState()

    var quietStart by remember(settings.quietHoursStartMinute) {

        mutableIntStateOf(settings.quietHoursStartMinute ?: 22 * 60)

    }

    var quietEnd by remember(settings.quietHoursEndMinute) {

        mutableIntStateOf(settings.quietHoursEndMinute ?: 7 * 60)

    }



    SettingsDetailScaffold(title = stringResource(R.string.notifications), onBack = onBack) {

        SettingToggleWithHelp(

            label = stringResource(R.string.usage_hud),

            help = stringResource(R.string.usage_hud_help),

            checked = settings.showSessionTimerNotification,

        ) {

            viewModel.update { s -> s.copy(showSessionTimerNotification = it, hudEnabled = it) }

        }

        SettingToggleWithHelp(

            label = stringResource(R.string.limit_warnings),

            help = stringResource(R.string.limit_warnings_help),

            checked = settings.warningAlertsEnabled,

        ) {

            viewModel.update { s -> s.copy(warningAlertsEnabled = it) }

        }

        SettingToggleWithHelp(

            label = stringResource(R.string.weekly_report),

            help = stringResource(R.string.weekly_report_help),

            checked = settings.weeklyReportEnabled,

        ) {

            viewModel.update { s -> s.copy(weeklyReportEnabled = it) }

        }

        Text(stringResource(R.string.quiet_hours), style = MaterialTheme.typography.labelMedium)

        com.gatekeep.app.ui.components.TimeOfDayPicker(

            stringResource(R.string.start),

            quietStart,

            onTimeChange = { quietStart = it },

        )

        com.gatekeep.app.ui.components.TimeOfDayPicker(

            stringResource(R.string.end),

            quietEnd,

            onTimeChange = { quietEnd = it },

        )

        androidx.compose.material3.Button(

            onClick = {

                viewModel.update { s ->

                    s.copy(quietHoursStartMinute = quietStart, quietHoursEndMinute = quietEnd)

                }

            },

            modifier = Modifier.fillMaxWidth(),

        ) { Text(stringResource(R.string.save_quiet_hours)) }

    }

}



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun EnforcementSettingsScreen(

    onBack: () -> Unit,

    viewModel: SettingsViewModel = hiltViewModel(),

) {

    val settings by viewModel.settings.collectAsState()



    SettingsDetailScaffold(title = stringResource(R.string.enforcement), onBack = onBack) {

        SettingToggleWithHelp(

            label = stringResource(R.string.enforcement_enabled),

            help = stringResource(R.string.enforcement_enabled_help),

            checked = settings.enforcementEnabled,

        ) {

            viewModel.update { s -> s.copy(enforcementEnabled = it) }

        }

    }

}



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun AboutSettingsScreen(

    onBack: () -> Unit,

    viewModel: SettingsViewModel = hiltViewModel(),

) {

    SettingsDetailScaffold(title = stringResource(R.string.about), onBack = onBack) {

        Text(

            stringResource(R.string.version_full_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),

            style = MaterialTheme.typography.bodyLarge,

        )

        Text(

            if (viewModel.lastEnforcementError() != null) {

                stringResource(R.string.last_error_format, viewModel.lastEnforcementError()!!)

            } else {

                stringResource(R.string.no_recent_enforcement_errors)

            },

            style = MaterialTheme.typography.bodySmall,

            modifier = Modifier.padding(top = 12.dp),

        )

    }

}



@OptIn(ExperimentalMaterial3Api::class)

@Composable

internal fun SettingsDetailScaffold(

    title: String,

    onBack: () -> Unit,

    content: @Composable () -> Unit,

) {

    Scaffold(

        topBar = {

            TopAppBar(

                title = { Text(title) },

                navigationIcon = {

                    IconButton(onClick = onBack) {

                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))

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

            content()

        }

    }

}



private fun enableDeviceAdmin(context: Context) {

    val component = ComponentName(context, GatekeepDeviceAdminReceiver::class.java)

    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {

        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)

        putExtra(

            DevicePolicyManager.EXTRA_ADD_EXPLANATION,

            context.getString(R.string.device_admin_explanation),

        )

    }

    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    context.startActivity(intent)

}


