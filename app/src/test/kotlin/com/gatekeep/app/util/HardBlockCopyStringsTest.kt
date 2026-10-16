package com.gatekeep.app.util

import com.gatekeep.app.R
import com.gatekeep.app.ui.profiles.limitActionLabelRes
import com.gatekeep.app.ui.profiles.sessionActionLabelRes
import com.gatekeep.domain.model.BlockReason
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnSessionLimitAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HardBlockCopyStringsTest {

    @Test
    fun sessionHardBlockOverlayUsesSessionLeaveCopy() {
        assertEquals(
            R.string.block_session_hard_block,
            BlockMessageResolver.blockMessageRes(
                BlockReason.sessionLimit,
                bypassAllowed = false,
            ),
        )
    }

    @Test
    fun periodHardBlockOverlayUsesResetCopyNotSessionLeave() {
        assertEquals(
            R.string.block_daily_hard_block,
            BlockMessageResolver.blockMessageRes(
                BlockReason.dailyLimit,
                bypassAllowed = false,
            ),
        )
        assertEquals(
            R.string.block_hourly_hard_block,
            BlockMessageResolver.blockMessageRes(
                BlockReason.hourlyLimit,
                bypassAllowed = false,
            ),
        )
        assertEquals(
            R.string.block_weekly_hard_block,
            BlockMessageResolver.blockMessageRes(
                BlockReason.weeklyLimit,
                bypassAllowed = false,
            ),
        )
        assertNotEquals(
            R.string.block_session_hard_block,
            BlockMessageResolver.blockMessageRes(
                BlockReason.dailyLimit,
                bypassAllowed = false,
            ),
        )
    }

    @Test
    fun waitOverlayKeepsGenericLimitCopy() {
        assertEquals(
            R.string.block_session_limit,
            BlockMessageResolver.blockMessageRes(
                BlockReason.sessionLimit,
                bypassAllowed = true,
            ),
        )
        assertEquals(
            R.string.block_daily_limit,
            BlockMessageResolver.blockMessageRes(
                BlockReason.dailyLimit,
                bypassAllowed = true,
            ),
        )
    }

    @Test
    fun sessionRulesLabelNamesThisSession() {
        assertEquals(
            R.string.session_action_hard_block,
            sessionActionLabelRes(OnSessionLimitAction.hardBlock),
        )
        assertEquals(
            R.string.limit_action_hard_block,
            limitActionLabelRes(OnLimitAction.hardBlock),
        )
        assertNotEquals(
            sessionActionLabelRes(OnSessionLimitAction.hardBlock),
            limitActionLabelRes(OnLimitAction.hardBlock),
        )
    }
}
