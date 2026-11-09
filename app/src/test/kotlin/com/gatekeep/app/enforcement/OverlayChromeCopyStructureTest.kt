package com.gatekeep.app.enforcement

import com.gatekeep.app.R
import com.gatekeep.domain.model.BlockPresentationReason
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class OverlayChromeCopyStructureTest(
    private val reason: BlockPresentationReason,
    private val chromeTitleRes: Int,
    private val bodyMessageRes: Int,
) {

    @Test
    fun chromeTitleResource_differsFromBodyMessageResource() {
        assertNotEquals(
            "Title and body must not share the same string for $reason",
            chromeTitleRes,
            bodyMessageRes,
        )
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun overlayCopyPairs(): List<Array<Any>> = listOf(
            arrayOf(BlockPresentationReason.openGate, R.string.overlay_chrome_on_open, R.string.wait_before_opening),
            arrayOf(BlockPresentationReason.sessionLimit, R.string.overlay_chrome_session, R.string.block_session_limit),
            arrayOf(BlockPresentationReason.dailyLimit, R.string.overlay_chrome_daily, R.string.block_daily_limit),
            arrayOf(BlockPresentationReason.hourlyLimit, R.string.overlay_chrome_hourly, R.string.block_hourly_limit),
            arrayOf(BlockPresentationReason.weeklyLimit, R.string.overlay_chrome_weekly, R.string.block_weekly_limit),
            arrayOf(BlockPresentationReason.scheduleBlock, R.string.overlay_chrome_schedule, R.string.block_schedule_block),
            arrayOf(BlockPresentationReason.outsideSchedule, R.string.overlay_chrome_schedule, R.string.block_outside_schedule),
            arrayOf(BlockPresentationReason.focusMode, R.string.overlay_chrome_focus, R.string.block_focus_mode),
            arrayOf(BlockPresentationReason.onBreak, R.string.overlay_chrome_break, R.string.block_take_break),
            arrayOf(BlockPresentationReason.extensionDenied, R.string.overlay_chrome_extension, R.string.extension_denied_not_allowed),
            arrayOf(BlockPresentationReason.profilePin, R.string.overlay_chrome_profile_pin, R.string.enter_profile_pin_to_open),
            arrayOf(BlockPresentationReason.unknown, R.string.overlay_chrome_blocked, R.string.block_generic),
        )
    }
}
