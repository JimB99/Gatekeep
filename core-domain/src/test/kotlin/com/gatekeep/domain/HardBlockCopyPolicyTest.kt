package com.gatekeep.domain

import com.gatekeep.domain.model.BlockReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class HardBlockCopyPolicyTest {

    @Test
    fun sessionHardBlock_isSittingUntilLeave() {
        assertEquals(
            HardBlockCopyPolicy.OverlayCopy.SessionUntilLeave,
            HardBlockCopyPolicy.overlayCopy(
                reason = BlockReason.sessionLimit,
                bypassAllowed = false,
                breakUntilEpochMs = null,
            ),
        )
    }

    @Test
    fun periodHardBlock_isUntilThatPeriodResets() {
        assertEquals(
            HardBlockCopyPolicy.OverlayCopy.HourUntilReset,
            HardBlockCopyPolicy.overlayCopy(BlockReason.hourlyLimit, bypassAllowed = false, null),
        )
        assertEquals(
            HardBlockCopyPolicy.OverlayCopy.DayUntilReset,
            HardBlockCopyPolicy.overlayCopy(BlockReason.dailyLimit, bypassAllowed = false, null),
        )
        assertEquals(
            HardBlockCopyPolicy.OverlayCopy.WeekUntilReset,
            HardBlockCopyPolicy.overlayCopy(BlockReason.weeklyLimit, bypassAllowed = false, null),
        )
    }

    @Test
    fun waitAndExtendStayGeneric() {
        assertEquals(
            HardBlockCopyPolicy.OverlayCopy.Generic,
            HardBlockCopyPolicy.overlayCopy(BlockReason.sessionLimit, bypassAllowed = true, null),
        )
        assertEquals(
            HardBlockCopyPolicy.OverlayCopy.Generic,
            HardBlockCopyPolicy.overlayCopy(BlockReason.dailyLimit, bypassAllowed = true, null),
        )
    }

    @Test
    fun breakStaysGeneric() {
        assertEquals(
            HardBlockCopyPolicy.OverlayCopy.Generic,
            HardBlockCopyPolicy.overlayCopy(
                BlockReason.onBreak,
                bypassAllowed = false,
                breakUntilEpochMs = 1_000L,
            ),
        )
        assertEquals(
            HardBlockCopyPolicy.OverlayCopy.Generic,
            HardBlockCopyPolicy.overlayCopy(
                BlockReason.dailyLimit,
                bypassAllowed = false,
                breakUntilEpochMs = 1_000L,
            ),
        )
    }

    @Test
    fun sessionAndPeriodCopyKindsDiffer() {
        assertNotEquals(
            HardBlockCopyPolicy.overlayCopy(BlockReason.sessionLimit, false, null),
            HardBlockCopyPolicy.overlayCopy(BlockReason.dailyLimit, false, null),
        )
    }
}
