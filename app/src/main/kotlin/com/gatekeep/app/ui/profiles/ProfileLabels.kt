package com.gatekeep.app.ui.profiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gatekeep.app.R
import com.gatekeep.domain.model.FrictionDifficulty
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.OnSessionLimitAction

@Composable
fun openActionLabel(action: OnOpenAction): String = when (action) {
    OnOpenAction.none -> stringResource(R.string.open_action_none)
    OnOpenAction.pinGate -> stringResource(R.string.open_action_pin)
    OnOpenAction.deterrentMath -> stringResource(R.string.open_action_math)
    OnOpenAction.deterrentWait -> stringResource(R.string.open_action_wait)
}

@Composable
fun limitActionLabel(action: OnLimitAction): String = when (action) {
    OnLimitAction.notifyOnly -> stringResource(R.string.limit_action_notify)
    OnLimitAction.limitWithExtensions -> stringResource(R.string.limit_action_extend)
    OnLimitAction.deterrentMath -> stringResource(R.string.open_action_math)
    OnLimitAction.deterrentWait -> stringResource(R.string.open_action_wait)
    OnLimitAction.mandatoryBreak -> stringResource(R.string.limit_action_break)
    OnLimitAction.hardBlock -> stringResource(R.string.limit_action_hard_block)
}

@Composable
fun sessionActionLabel(action: OnSessionLimitAction): String = when (action) {
    OnSessionLimitAction.notifyOnly -> stringResource(R.string.limit_action_notify)
    OnSessionLimitAction.deterrentMath -> stringResource(R.string.open_action_math)
    OnSessionLimitAction.deterrentWait -> stringResource(R.string.open_action_wait)
    OnSessionLimitAction.limitWithExtensions -> stringResource(R.string.limit_action_extend)
    OnSessionLimitAction.mandatoryBreak -> stringResource(R.string.limit_action_break)
    OnSessionLimitAction.hardBlock -> stringResource(R.string.limit_action_hard_block)
}

@Composable
fun frictionDifficultyLabel(difficulty: FrictionDifficulty): String = when (difficulty) {
    FrictionDifficulty.easy -> stringResource(R.string.difficulty_easy)
    FrictionDifficulty.medium -> stringResource(R.string.difficulty_medium)
    FrictionDifficulty.hard -> stringResource(R.string.difficulty_hard)
}

@Composable
fun schedulePolicyModeLabel(mode: com.gatekeep.domain.model.SchedulePolicyMode): String =
    when (mode) {
        com.gatekeep.domain.model.SchedulePolicyMode.allow ->
            stringResource(R.string.schedule_mode_allow)
        com.gatekeep.domain.model.SchedulePolicyMode.block ->
            stringResource(R.string.schedule_mode_block)
        com.gatekeep.domain.model.SchedulePolicyMode.default ->
            stringResource(R.string.schedule_mode_default)
        com.gatekeep.domain.model.SchedulePolicyMode.customize ->
            stringResource(R.string.schedule_mode_customize)
    }

@Composable
fun schedulePolicyModeChipLabel(mode: com.gatekeep.domain.model.SchedulePolicyMode): String =
    when (mode) {
        com.gatekeep.domain.model.SchedulePolicyMode.allow ->
            stringResource(R.string.schedule_mode_allow_short)
        com.gatekeep.domain.model.SchedulePolicyMode.block ->
            stringResource(R.string.schedule_mode_block_short)
        com.gatekeep.domain.model.SchedulePolicyMode.default ->
            stringResource(R.string.schedule_mode_default_short)
        com.gatekeep.domain.model.SchedulePolicyMode.customize ->
            stringResource(R.string.schedule_mode_customize_short)
    }
