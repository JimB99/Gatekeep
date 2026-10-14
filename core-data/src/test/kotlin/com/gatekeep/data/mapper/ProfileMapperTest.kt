package com.gatekeep.data.mapper

import com.gatekeep.data.local.entity.ProfileEntity
import com.gatekeep.domain.model.FrictionDifficulty
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.OnSessionLimitAction
import com.gatekeep.domain.model.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileMapperTest {

    @Test
    fun `entity round trip preserves refill continue and per-axis difficulty`() {
        val profile = Profile(
            id = 9,
            name = "Work",
            isActive = true,
            sessionLimitMs = 20 * 60_000L,
            onOpenAction = OnOpenAction.deterrentMath,
            onLimitAction = OnLimitAction.deterrentWait,
            onSessionLimitAction = OnSessionLimitAction.deterrentMath,
            defaultFrictionDifficulty = FrictionDifficulty.medium,
            sessionRefillOverrideMs = 8 * 60_000L,
            periodContinueInheritSessionCap = false,
            periodContinueDurationMs = 12 * 60_000L,
            openFrictionDifficulty = FrictionDifficulty.easy,
            periodFrictionDifficulty = FrictionDifficulty.medium,
            sessionFrictionDifficulty = FrictionDifficulty.hard,
        )

        val restored = profile.toEntity().toDomain()

        assertEquals(profile.sessionRefillOverrideMs, restored.sessionRefillOverrideMs)
        assertEquals(false, restored.periodContinueInheritSessionCap)
        assertEquals(12 * 60_000L, restored.periodContinueDurationMs)
        assertEquals(FrictionDifficulty.easy, restored.openFrictionDifficulty)
        assertEquals(FrictionDifficulty.medium, restored.periodFrictionDifficulty)
        assertEquals(FrictionDifficulty.hard, restored.sessionFrictionDifficulty)
        assertEquals(profile.sessionLimitMs, restored.sessionLimitMs)
    }

    @Test
    fun `null refill override survives persist`() {
        val profile = Profile(id = 1, name = "P", isActive = true, sessionRefillOverrideMs = null)
        assertNull(profile.toEntity().toDomain().sessionRefillOverrideMs)
    }

    @Test
    fun `new profile persist keeps continue inherit on without a session cap`() {
        val profile = Profile(id = 1, name = "P", isActive = true)
        val restored = profile.toEntity().toDomain()
        assertTrue(restored.periodContinueInheritSessionCap)
        assertNull(restored.sessionLimitMs)
    }

    @Test
    fun `new entity defaults continue inherit on`() {
        assertTrue(ProfileEntity(name = "P").periodContinueInheritSessionCap)
    }
}
