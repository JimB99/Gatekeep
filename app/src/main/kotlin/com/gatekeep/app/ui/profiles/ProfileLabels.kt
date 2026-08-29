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
