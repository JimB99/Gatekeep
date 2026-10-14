package com.gatekeep.app.util

import android.content.Context
import com.gatekeep.app.R
import com.gatekeep.app.enforcement.OverlayChromePolicy
import com.gatekeep.app.enforcement.OverlayChromeTitle
import com.gatekeep.domain.ExtensionDenialReason
import com.gatekeep.domain.HardBlockCopyPolicy
import com.gatekeep.domain.SessionContinuityGrace
import com.gatekeep.domain.model.BlockPresentationReason
import com.gatekeep.domain.model.BlockReason
import com.gatekeep.domain.model.FrictionMethod

object BlockMessageResolver {

    fun blockMessageRes(
        reason: BlockReason,
        bypassAllowed: Boolean = true,
        breakUntilEpochMs: Long? = null,
    ): Int = when (HardBlockCopyPolicy.overlayCopy(reason, bypassAllowed, breakUntilEpochMs)) {
        HardBlockCopyPolicy.OverlayCopy.SessionUntilLeave -> R.string.block_session_hard_block
        HardBlockCopyPolicy.OverlayCopy.HourUntilReset -> R.string.block_hourly_hard_block
        HardBlockCopyPolicy.OverlayCopy.DayUntilReset -> R.string.block_daily_hard_block
        HardBlockCopyPolicy.OverlayCopy.WeekUntilReset -> R.string.block_weekly_hard_block
        HardBlockCopyPolicy.OverlayCopy.Generic -> genericBlockMessageRes(reason)
    }

    fun blockMessage(
        context: Context,
        reason: BlockReason,
        appLabel: String? = null,
        bypassAllowed: Boolean = true,
        breakUntilEpochMs: Long? = null,
        sessionContinuityGraceMs: Long = SessionContinuityGrace.DEFAULT_MS,
    ): String {
        val res = blockMessageRes(reason, bypassAllowed, breakUntilEpochMs)
        return if (res == R.string.block_session_hard_block) {
            context.getString(res, formatDurationMs(context, sessionContinuityGraceMs))
        } else {
            context.getString(res)
        }
    }

    private fun genericBlockMessageRes(reason: BlockReason): Int = when (reason) {
        BlockReason.focusMode -> R.string.block_focus_mode
        BlockReason.outsideSchedule -> R.string.block_outside_schedule
        BlockReason.scheduleBlock -> R.string.block_schedule_block
        BlockReason.onBreak -> R.string.block_take_break
        BlockReason.sessionLimit -> R.string.block_session_limit
        BlockReason.dailyLimit -> R.string.block_daily_limit
        BlockReason.hourlyLimit -> R.string.block_hourly_limit
        BlockReason.weeklyLimit -> R.string.block_weekly_limit
        BlockReason.notMonitored,
        BlockReason.profilePaused,
        BlockReason.appPaused,
        -> R.string.block_generic
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
        FrictionMethod.password -> context.getString(R.string.enter_profile_pin)
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

    fun chromeTitle(context: Context, reason: BlockPresentationReason): String =
        when (OverlayChromePolicy.chrome(reason).title) {
            OverlayChromeTitle.OnOpenWait -> context.getString(R.string.overlay_chrome_on_open)
            OverlayChromeTitle.SessionLimit -> context.getString(R.string.overlay_chrome_session)
            OverlayChromeTitle.DailyLimit -> context.getString(R.string.overlay_chrome_daily)
            OverlayChromeTitle.HourlyLimit -> context.getString(R.string.overlay_chrome_hourly)
            OverlayChromeTitle.WeeklyLimit -> context.getString(R.string.overlay_chrome_weekly)
            OverlayChromeTitle.ReasonLabel -> reasonLabel(context, reason)
        }

    fun reasonLabel(context: Context, reason: BlockPresentationReason): String = when (reason) {
        BlockPresentationReason.outsideSchedule -> context.getString(R.string.block_outside_schedule)
        BlockPresentationReason.scheduleBlock -> context.getString(R.string.block_schedule_block)
        BlockPresentationReason.openGate -> context.getString(R.string.block_open_gate)
        BlockPresentationReason.profilePin -> context.getString(R.string.enter_profile_pin)
        BlockPresentationReason.extensionDenied -> context.getString(R.string.extension_denied_title)
        BlockPresentationReason.focusMode -> context.getString(R.string.block_focus_mode)
        BlockPresentationReason.onBreak -> context.getString(R.string.block_take_break)
        BlockPresentationReason.sessionLimit -> context.getString(R.string.block_session_limit)
        BlockPresentationReason.dailyLimit -> context.getString(R.string.block_daily_limit)
        BlockPresentationReason.hourlyLimit -> context.getString(R.string.block_hourly_limit)
        BlockPresentationReason.weeklyLimit -> context.getString(R.string.block_weekly_limit)
        else -> context.getString(R.string.block_generic)
    }
}
