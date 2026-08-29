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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gatekeep.app.R
import com.gatekeep.app.ui.components.DurationPickerWithSeconds
import com.gatekeep.app.ui.components.GatekeepFilterChip
import com.gatekeep.app.ui.components.SaveChangesButton
import com.gatekeep.app.ui.components.rememberUnsavedChangesGuard
import com.gatekeep.app.ui.viewmodel.ProfileViewModel
import com.gatekeep.domain.model.FrictionDifficulty
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.OnSessionLimitAction
import com.gatekeep.domain.model.Profile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileRulesScreen(
    profileId: Long,
    onBack: () -> Unit,
    onNavigateOpenRule: () -> Unit,
    onNavigateLimitRule: () -> Unit,
    onNavigateSessionRule: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsState()
    val profile = profiles.find { it.id == profileId }
    val saveMessage by viewModel.saveMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var savedExtensionDraft by remember(profile?.id) {
        mutableStateOf(
            ExtensionPolicyDraft.fromPolicy(
                profile?.extensionPolicy ?: com.gatekeep.domain.model.ExtensionPolicy(),
            ),
        )
    }
    var extensionDraft by remember(profile?.id) { mutableStateOf(savedExtensionDraft) }

    LaunchedEffect(profile?.extensionPolicy) {
        profile?.extensionPolicy?.let {
            val loaded = ExtensionPolicyDraft.fromPolicy(it)
            savedExtensionDraft = loaded
            extensionDraft = loaded
        }
    }

    val extensionDirty = extensionDraft != savedExtensionDraft

    fun saveExtension() {
        profile?.let { p ->
            viewModel.saveProfile(p.copy(extensionPolicy = extensionDraft.toPolicy()))
            savedExtensionDraft = extensionDraft
        }
    }

    val backGuard = rememberUnsavedChangesGuard(
        isDirty = extensionDirty,
        onNavigateBack = onBack,
        onSave = ::saveExtension,
        onDiscardChanges = { extensionDraft = savedExtensionDraft },
    )

    val showExtensionSection = profile?.onLimitAction == OnLimitAction.limitWithExtensions ||
        profile?.onSessionLimitAction == OnSessionLimitAction.limitWithExtensions

    LaunchedEffect(saveMessage) {
        saveMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSaveMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rules)) },
                navigationIcon = {
                    IconButton(onClick = backGuard::navigateBack) {
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
            RuleNavRow(
                title = stringResource(R.string.when_opening_app),
                subtitle = openActionLabel(profile?.onOpenAction ?: OnOpenAction.none),
                onClick = onNavigateOpenRule,
            )
            RuleNavRow(
                title = stringResource(R.string.when_limit_reached),
                subtitle = limitActionLabel(profile?.onLimitAction ?: OnLimitAction.limitWithExtensions),
                onClick = onNavigateLimitRule,
            )
            RuleNavRow(
                title = stringResource(R.string.during_use_session),
                subtitle = sessionActionLabel(profile?.onSessionLimitAction ?: OnSessionLimitAction.limitWithExtensions),
                onClick = onNavigateSessionRule,
            )
            if (showExtensionSection) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.extension_options), fontWeight = FontWeight.SemiBold)
                    ExtensionPolicyEditor(
                        draft = extensionDraft,
                        onDraftChange = { extensionDraft = it },
                    )
                    SaveChangesButton(
                        visible = extensionDirty,
                        onClick = ::saveExtension,
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleNavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
    ) {
        ListItem(
            headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
            supportingContent = { Text(subtitle) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleOpenActionScreen(
    profileId: Long,
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsState()
    val profile = profiles.find { it.id == profileId }
    var savedOnOpen by remember(profile?.id) { mutableStateOf(profile?.onOpenAction ?: OnOpenAction.none) }
    var onOpen by remember(profile?.id) { mutableStateOf(savedOnOpen) }
    var savedDifficulty by remember(profile?.id) {
        mutableStateOf(profile?.defaultFrictionDifficulty ?: FrictionDifficulty.medium)
    }
    var difficulty by remember(profile?.id) { mutableStateOf(savedDifficulty) }
    var savedOpenWaitSeconds by remember(profile?.id) {
        mutableIntStateOf(profile?.openWaitDurationSeconds ?: 60)
    }
    var openWaitSeconds by remember(profile?.id) { mutableIntStateOf(savedOpenWaitSeconds) }
    var profilePin by remember { mutableStateOf("") }
    var savedPin by remember { mutableStateOf("") }

    LaunchedEffect(profile?.onOpenAction, profile?.defaultFrictionDifficulty, profile?.openWaitDurationSeconds) {
        if (profile == null) return@LaunchedEffect
        savedOnOpen = profile.onOpenAction
        onOpen = savedOnOpen
        savedDifficulty = profile.defaultFrictionDifficulty
        difficulty = savedDifficulty
        savedOpenWaitSeconds = profile.openWaitDurationSeconds
        openWaitSeconds = savedOpenWaitSeconds
    }

    LaunchedEffect(profileId, profile?.passwordHash) {
        val loaded = if (!profile?.passwordHash.isNullOrBlank()) {
            viewModel.loadProfilePin(profileId).orEmpty()
        } else {
            ""
        }
        profilePin = loaded
        savedPin = loaded
    }

    val isDirty = onOpen != savedOnOpen ||
        difficulty != savedDifficulty ||
        openWaitSeconds != savedOpenWaitSeconds

    fun saveRules() {
        val p = profile ?: return
        viewModel.saveProfile(
            p.copy(
                onOpenAction = onOpen,
                defaultFrictionDifficulty = difficulty,
                openWaitDurationSeconds = openWaitSeconds.coerceIn(1, 3600),
                lockEnabled = onOpen == OnOpenAction.pinGate && !p.passwordHash.isNullOrBlank(),
            ),
        )
        savedOnOpen = onOpen
        savedDifficulty = difficulty
        savedOpenWaitSeconds = openWaitSeconds
    }

    fun discardChanges() {
        onOpen = savedOnOpen
        difficulty = savedDifficulty
        openWaitSeconds = savedOpenWaitSeconds
    }

    val backGuard = rememberUnsavedChangesGuard(
        isDirty = isDirty,
        onNavigateBack = onBack,
        onSave = ::saveRules,
        onDiscardChanges = ::discardChanges,
    )

    RuleDetailScaffold(
        title = stringResource(R.string.when_opening_app),
        onBack = backGuard::navigateBack,
        isDirty = isDirty,
        onSave = ::saveRules,
    ) {
        OpenActionRuleEditor(
            onOpen = onOpen,
            difficulty = difficulty,
            openWaitSeconds = openWaitSeconds,
            profilePin = profilePin,
            savedPin = savedPin,
            profile = profile,
            viewModel = viewModel,
            onOpenChange = { onOpen = it },
            onDifficultyChange = { difficulty = it },
            onOpenWaitChange = { openWaitSeconds = it.coerceIn(1, 3600) },
            onPinChange = { profilePin = it },
            onPinSaved = { savedPin = it },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleLimitActionScreen(
    profileId: Long,
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsState()
    val profile = profiles.find { it.id == profileId }
    var savedOnLimit by remember(profile?.id) {
        mutableStateOf(profile?.onLimitAction ?: OnLimitAction.limitWithExtensions)
    }
    var onLimit by remember(profile?.id) { mutableStateOf(savedOnLimit) }

    LaunchedEffect(profile?.onLimitAction) {
        profile?.onLimitAction?.let {
            savedOnLimit = it
            onLimit = it
        }
    }

    val isDirty = onLimit != savedOnLimit

    fun saveRules() {
        profile?.let { p ->
            viewModel.saveProfile(p.copy(onLimitAction = onLimit))
            savedOnLimit = onLimit
        }
    }

    val backGuard = rememberUnsavedChangesGuard(
        isDirty = isDirty,
        onNavigateBack = onBack,
        onSave = ::saveRules,
        onDiscardChanges = { onLimit = savedOnLimit },
    )

    RuleDetailScaffold(
        title = stringResource(R.string.when_limit_reached),
        onBack = backGuard::navigateBack,
        isDirty = isDirty,
        onSave = ::saveRules,
    ) {
        LimitActionRuleEditor(onLimit = onLimit, onLimitChange = { onLimit = it })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleSessionActionScreen(
    profileId: Long,
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsState()
    val profile = profiles.find { it.id == profileId }
    var savedOnSession by remember(profile?.id) {
        mutableStateOf(profile?.onSessionLimitAction ?: OnSessionLimitAction.limitWithExtensions)
    }
    var onSession by remember(profile?.id) { mutableStateOf(savedOnSession) }
    var savedDifficulty by remember(profile?.id) {
        mutableStateOf(profile?.defaultFrictionDifficulty ?: FrictionDifficulty.medium)
    }
    var difficulty by remember(profile?.id) { mutableStateOf(savedDifficulty) }
    var savedSessionWaitSeconds by remember(profile?.id) {
        mutableIntStateOf(profile?.sessionWaitDurationSeconds ?: 60)
    }
    var sessionWaitSeconds by remember(profile?.id) { mutableIntStateOf(savedSessionWaitSeconds) }

    LaunchedEffect(profile?.onSessionLimitAction, profile?.defaultFrictionDifficulty, profile?.sessionWaitDurationSeconds) {
        if (profile == null) return@LaunchedEffect
        savedOnSession = profile.onSessionLimitAction
        onSession = savedOnSession
        savedDifficulty = profile.defaultFrictionDifficulty
        difficulty = savedDifficulty
        savedSessionWaitSeconds = profile.sessionWaitDurationSeconds
        sessionWaitSeconds = savedSessionWaitSeconds
    }

    val isDirty = onSession != savedOnSession ||
        difficulty != savedDifficulty ||
        sessionWaitSeconds != savedSessionWaitSeconds

    fun saveRules() {
        val p = profile ?: return
        viewModel.saveProfile(
            p.copy(
                onSessionLimitAction = onSession,
                defaultFrictionDifficulty = difficulty,
                sessionWaitDurationSeconds = sessionWaitSeconds.coerceIn(1, 3600),
            ),
        )
        savedOnSession = onSession
        savedDifficulty = difficulty
        savedSessionWaitSeconds = sessionWaitSeconds
    }

    fun discardChanges() {
        onSession = savedOnSession
        difficulty = savedDifficulty
        sessionWaitSeconds = savedSessionWaitSeconds
    }

    val backGuard = rememberUnsavedChangesGuard(
        isDirty = isDirty,
        onNavigateBack = onBack,
        onSave = ::saveRules,
        onDiscardChanges = ::discardChanges,
    )

    RuleDetailScaffold(
        title = stringResource(R.string.during_use_session),
        onBack = backGuard::navigateBack,
        isDirty = isDirty,
        onSave = ::saveRules,
    ) {
        SessionActionRuleEditor(
            onSession = onSession,
            difficulty = difficulty,
            sessionWaitSeconds = sessionWaitSeconds,
            onSessionChange = { onSession = it },
            onDifficultyChange = { difficulty = it },
            onSessionWaitChange = { sessionWaitSeconds = it.coerceIn(1, 3600) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleDetailScaffold(
    title: String,
    onBack: () -> Unit,
    isDirty: Boolean = false,
    onSave: (() -> Unit)? = null,
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
            if (onSave != null) {
                SaveChangesButton(visible = isDirty, onClick = onSave)
            }
        }
    }
}

@Composable
internal fun OpenActionRuleEditor(
    onOpen: OnOpenAction,
    difficulty: FrictionDifficulty,
    openWaitSeconds: Int,
    profilePin: String,
    savedPin: String,
    profile: Profile?,
    viewModel: ProfileViewModel,
    onOpenChange: (OnOpenAction) -> Unit,
    onDifficultyChange: (FrictionDifficulty) -> Unit,
    onOpenWaitChange: (Int) -> Unit,
    onPinChange: (String) -> Unit,
    onPinSaved: (String) -> Unit,
) {
    RuleSection(title = stringResource(R.string.when_opening_app)) {
        RuleChipRow {
            GatekeepFilterChip(
                selected = onOpen == OnOpenAction.none,
                onClick = { onOpenChange(OnOpenAction.none) },
                label = { Text(openActionLabel(OnOpenAction.none)) },
            )
        }
        RuleDivider()
        RuleGroupLabel(stringResource(R.string.rules_group_deterrent))
        RuleChipRow {
            GatekeepFilterChip(
                selected = onOpen == OnOpenAction.deterrentWait,
                onClick = { onOpenChange(OnOpenAction.deterrentWait) },
                label = { Text(openActionLabel(OnOpenAction.deterrentWait)) },
            )
            GatekeepFilterChip(
                selected = onOpen == OnOpenAction.deterrentMath,
                onClick = { onOpenChange(OnOpenAction.deterrentMath) },
                label = { Text(openActionLabel(OnOpenAction.deterrentMath)) },
            )
        }
        RuleDivider()
        RuleGroupLabel(stringResource(R.string.rules_group_block))
        RuleChipRow {
            GatekeepFilterChip(
                selected = onOpen == OnOpenAction.pinGate,
                onClick = { onOpenChange(OnOpenAction.pinGate) },
                label = { Text(openActionLabel(OnOpenAction.pinGate)) },
            )
        }
        if (onOpen == OnOpenAction.pinGate) {
            ProfilePinEditor(
                pin = profilePin,
                savedPin = savedPin,
                onPinChange = onPinChange,
                label = stringResource(R.string.profile_pin),
                hint = stringResource(R.string.pin_gate_rules_hint),
                profile = profile,
                viewModel = viewModel,
                pinGateActive = true,
                onSaved = onPinSaved,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (onOpen == OnOpenAction.deterrentMath) {
            Text(
                stringResource(R.string.math_difficulty),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FrictionDifficulty.entries.forEach { d ->
                    GatekeepFilterChip(
                        selected = difficulty == d,
                        onClick = { onDifficultyChange(d) },
                        label = { Text(frictionDifficultyLabel(d)) },
                    )
                }
            }
        }
        if (onOpen == OnOpenAction.deterrentWait) {
            DurationPickerWithSeconds(
                label = stringResource(R.string.wait_before_opening_label),
                totalSeconds = openWaitSeconds,
                onDurationChange = onOpenWaitChange,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
internal fun LimitActionRuleEditor(
    onLimit: OnLimitAction,
    onLimitChange: (OnLimitAction) -> Unit,
) {
    RuleSection(title = stringResource(R.string.when_limit_reached)) {
        RuleChipRow {
            GatekeepFilterChip(
                selected = onLimit == OnLimitAction.notifyOnly,
                onClick = { onLimitChange(OnLimitAction.notifyOnly) },
                label = { Text(limitActionLabel(OnLimitAction.notifyOnly)) },
            )
        }
        RuleDivider()
        RuleGroupLabel(stringResource(R.string.rules_group_extend))
        RuleChipRow {
            GatekeepFilterChip(
                selected = onLimit == OnLimitAction.limitWithExtensions,
                onClick = { onLimitChange(OnLimitAction.limitWithExtensions) },
                label = { Text(limitActionLabel(OnLimitAction.limitWithExtensions)) },
            )
        }
        RuleDivider()
        RuleGroupLabel(stringResource(R.string.rules_group_block))
        RuleChipRow {
            GatekeepFilterChip(
                selected = onLimit == OnLimitAction.hardBlock,
                onClick = { onLimitChange(OnLimitAction.hardBlock) },
                label = { Text(limitActionLabel(OnLimitAction.hardBlock)) },
            )
        }
    }
}

@Composable
internal fun SessionActionRuleEditor(
    onSession: OnSessionLimitAction,
    difficulty: FrictionDifficulty,
    sessionWaitSeconds: Int,
    onSessionChange: (OnSessionLimitAction) -> Unit,
    onDifficultyChange: (FrictionDifficulty) -> Unit,
    onSessionWaitChange: (Int) -> Unit,
) {
    RuleSection(title = stringResource(R.string.during_use_session)) {
        RuleChipRow {
            GatekeepFilterChip(
                selected = onSession == OnSessionLimitAction.notifyOnly,
                onClick = { onSessionChange(OnSessionLimitAction.notifyOnly) },
                label = { Text(sessionActionLabel(OnSessionLimitAction.notifyOnly)) },
            )
        }
        RuleDivider()
        RuleGroupLabel(stringResource(R.string.rules_group_deterrent))
        RuleChipRow {
            GatekeepFilterChip(
                selected = onSession == OnSessionLimitAction.deterrentWait,
                onClick = { onSessionChange(OnSessionLimitAction.deterrentWait) },
                label = { Text(sessionActionLabel(OnSessionLimitAction.deterrentWait)) },
            )
            GatekeepFilterChip(
                selected = onSession == OnSessionLimitAction.deterrentMath,
                onClick = { onSessionChange(OnSessionLimitAction.deterrentMath) },
                label = { Text(sessionActionLabel(OnSessionLimitAction.deterrentMath)) },
            )
        }
        RuleDivider()
        RuleGroupLabel(stringResource(R.string.rules_group_extend))
        RuleChipRow {
            GatekeepFilterChip(
                selected = onSession == OnSessionLimitAction.limitWithExtensions,
                onClick = { onSessionChange(OnSessionLimitAction.limitWithExtensions) },
                label = { Text(sessionActionLabel(OnSessionLimitAction.limitWithExtensions)) },
            )
        }
        RuleDivider()
        RuleGroupLabel(stringResource(R.string.rules_group_block))
        RuleChipRow {
            GatekeepFilterChip(
                selected = onSession == OnSessionLimitAction.hardBlock,
                onClick = { onSessionChange(OnSessionLimitAction.hardBlock) },
                label = { Text(sessionActionLabel(OnSessionLimitAction.hardBlock)) },
            )
        }
        if (onSession == OnSessionLimitAction.deterrentMath) {
            Text(
                stringResource(R.string.math_difficulty),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FrictionDifficulty.entries.forEach { d ->
                    GatekeepFilterChip(
                        selected = difficulty == d,
                        onClick = { onDifficultyChange(d) },
                        label = { Text(frictionDifficultyLabel(d)) },
                    )
                }
            }
        }
        if (onSession == OnSessionLimitAction.deterrentWait) {
            DurationPickerWithSeconds(
                label = stringResource(R.string.wait_during_use),
                totalSeconds = sessionWaitSeconds,
                onDurationChange = onSessionWaitChange,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
