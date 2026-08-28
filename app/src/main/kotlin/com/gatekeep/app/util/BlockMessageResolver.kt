package com.gatekeep.app.util

import android.content.Context
import com.gatekeep.app.R
import com.gatekeep.domain.ExtensionDenialReason
import com.gatekeep.domain.model.BlockReason
import com.gatekeep.domain.model.FrictionMethod

object BlockMessageResolver {

    fun blockMessage(context: Context, reason: BlockReason, appLabel: String? = null): String =
        when (reason) {
            BlockReason.focusMode -> context.getString(R.string.block_focus_mode)
            BlockReason.outsideSchedule -> context.getString(R.string.block_outside_schedule)
            BlockReason.onBreak -> context.getString(R.string.block_take_break)
            BlockReason.sessionLimit -> context.getString(R.string.block_session_limit)
            BlockReason.dailyLimit -> context.getString(R.string.block_daily_limit)
            BlockReason.hourlyLimit -> context.getString(R.string.block_hourly_limit)
            BlockReason.weeklyLimit -> context.getString(R.string.block_weekly_limit)
            BlockReason.notMonitored,
            BlockReason.profilePaused,
            BlockReason.appPaused,
            -> context.getString(R.string.block_generic)
        }

    fun limitReachedForApp(context: Context, appLabel: String): String =
        context.getString(R.string.usage_limit_reached_for_app, appLabel)

    fun enterProfilePin(context: Context, appLabel: String): String =
        context.getString(R.string.enter_profile_pin_to_open, appLabel)

    fun waitBeforeOpening(context: Context): String =
        context.getString(R.string.wait_before_opening)

    fun completeChallengeToOpen(context: Context): String =
        context.getString(R.string.complete_challenge_to_open)

    fun delayOpenMessage(context: Context): String = waitBeforeOpening(context)

    fun openDeterrentMessage(context: Context, method: FrictionMethod): String = when (method) {
        FrictionMethod.math -> completeChallengeToOpen(context)
        FrictionMethod.waitOneMin -> waitBeforeOpening(context)
        else -> waitBeforeOpening(context)
    }

    fun extensionDenied(context: Context, reason: ExtensionDenialReason): String =
        when (reason) {
            ExtensionDenialReason.noLimitTodayDisabled ->
                context.getString(R.string.extension_denied_no_limit_today)
            ExtensionDenialReason.extensionNotAllowed ->
                context.getString(R.string.extension_denied_not_allowed)
            ExtensionDenialReason.dailyLimitReached ->
                context.getString(R.string.extension_denied_daily_limit)
            ExtensionDenialReason.tooManyConsecutive ->
                context.getString(R.string.extension_denied_consecutive)
        }

    fun reasonLabel(context: Context, reasonKey: String): String = when (reasonKey) {
        "outsideSchedule" -> context.getString(R.string.block_outside_schedule)
        "openGate" -> context.getString(R.string.block_open_gate)
        "profilePin" -> context.getString(R.string.enter_profile_pin)
        "extensionDenied" -> context.getString(R.string.extension_denied_title)
        else -> reasonKey
    }
}
