package com.gatekeep.domain

import com.gatekeep.domain.model.BlockPresentationReason
import com.gatekeep.domain.model.FrictionDifficulty
import com.gatekeep.domain.model.Profile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OverlayFrictionDifficultyTest {

    private val profile = Profile(
        id = 1,
        name = "P",
        isActive = true,
        defaultFrictionDifficulty = FrictionDifficulty.easy,
        openFrictionDifficulty = FrictionDifficulty.hard,
        periodFrictionDifficulty = FrictionDifficulty.medium,
        sessionFrictionDifficulty = FrictionDifficulty.easy,
    )

    @Test
    fun `on-open math overlay uses the on-open axis`() {
        assertEquals(
            FrictionDifficulty.hard,
            profile.overlayFrictionDifficulty(BlockPresentationReason.openGate),
        )
    }

    @Test
    fun `period math overlay uses the period axis`() {
        assertEquals(
            FrictionDifficulty.medium,
            profile.overlayFrictionDifficulty(BlockPresentationReason.dailyLimit),
        )
        assertEquals(
            FrictionDifficulty.medium,
            profile.overlayFrictionDifficulty(BlockPresentationReason.hourlyLimit),
        )
        assertEquals(
            FrictionDifficulty.medium,
            profile.overlayFrictionDifficulty(BlockPresentationReason.weeklyLimit),
        )
    }

    @Test
    fun `session math overlay uses the session axis`() {
        assertEquals(
            FrictionDifficulty.easy,
            profile.overlayFrictionDifficulty(BlockPresentationReason.sessionLimit),
        )
        assertEquals(
            FrictionDifficulty.easy,
            profile.overlayFrictionDifficulty(BlockPresentationReason.onBreak),
        )
    }
}
