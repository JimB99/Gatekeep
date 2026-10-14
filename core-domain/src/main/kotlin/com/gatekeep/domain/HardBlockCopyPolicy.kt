package com.gatekeep.domain

import com.gatekeep.domain.model.BlockReason

/**
 * Overlay body for a no-bypass usage stop. Session hard block is sitting-only
 * (leave of the session continuity grace starts a new sitting). Period hard
 * block lasts until that hour/day/week resets.
 */
object HardBlockCopyPolicy {

    enum class OverlayCopy {
        Generic,
        SessionUntilLeave,
        HourUntilReset,
        DayUntilReset,
        WeekUntilReset,
    }

    fun overlayCopy(
        reason: BlockReason,
        bypassAllowed: Boolean,
        breakUntilEpochMs: Long?,
    ): OverlayCopy {
        if (bypassAllowed || breakUntilEpochMs != null) return OverlayCopy.Generic
        return when (reason) {
            BlockReason.sessionLimit -> OverlayCopy.SessionUntilLeave
            BlockReason.hourlyLimit -> OverlayCopy.HourUntilReset
            BlockReason.dailyLimit -> OverlayCopy.DayUntilReset
            BlockReason.weeklyLimit -> OverlayCopy.WeekUntilReset
            else -> OverlayCopy.Generic
        }
    }
}
