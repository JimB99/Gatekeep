package com.gatekeep.domain

import com.gatekeep.domain.model.FrictionDifficulty
import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnSessionLimitAction
import com.gatekeep.domain.model.Profile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LimitGrantDefaultsTest {

    @Test
    fun `new profile stays on default inherit so a later session cap selects the continue chip`() {
        val profile = Profile(id = 1, name = "New", isActive = true)
        assertTrue(profile.periodContinueInheritSessionCap)
        assertEquals(15 * 60_000L, profile.periodContinueDurationMs)
        assertFalse(
            LimitGrantDefaults.inheritChipSelected(
                profile.periodContinueInheritSessionCap,
                profile.sessionLimitMs,
            ),
        )
        assertTrue(
            LimitGrantDefaults.inheritChipSelected(
                profile.periodContinueInheritSessionCap,
                20 * 60_000L,
            ),
        )
    }

    @Test
    fun `continue inherit defaults on until the user picks custom`() {
        assertTrue(LimitGrantDefaults.defaultContinueInherit())
        assertTrue(LimitGrantDefaults.defaultContinueInherit(null))
        assertTrue(LimitGrantDefaults.defaultContinueInherit(0L))
        assertTrue(LimitGrantDefaults.defaultContinueInherit(20 * 60_000L))
    }

    @Test
    fun `persist inherit intent even when the chip is off because there is no session cap`() {
        assertTrue(LimitGrantDefaults.continueInheritToPersist(true))
        assertFalse(LimitGrantDefaults.inheritChipSelected(true, null))
        assertFalse(LimitGrantDefaults.continueInheritToPersist(false))
        assertFalse(LimitGrantDefaults.inheritChipSelected(false, 20 * 60_000L))
    }

    @Test
    fun `chip on requires both inherit flag and a session cap`() {
        assertTrue(LimitGrantDefaults.inheritChipSelected(true, 20 * 60_000L))
        assertFalse(LimitGrantDefaults.inheritChipSelected(true, null))
        assertFalse(LimitGrantDefaults.inheritChipSelected(false, 20 * 60_000L))
    }

    @Test
    fun `turning inherit off seeds custom duration from session cap`() {
        assertEquals(45 * 60_000L, LimitGrantDefaults.seedCustomContinueMs(45 * 60_000L))
    }

    @Test
    fun `turning inherit off without session cap seeds 15 min`() {
        assertEquals(
            LimitGrantDefaults.DEFAULT_PERIOD_CONTINUE_MS,
            LimitGrantDefaults.seedCustomContinueMs(null),
        )
        assertEquals(
            LimitGrantDefaults.DEFAULT_PERIOD_CONTINUE_MS,
            LimitGrantDefaults.seedCustomContinueMs(0L),
        )
    }

    @Test
    fun `custom continue stays custom after a session cap is added`() {
        val custom = Profile(
            id = 1,
            name = "New",
            isActive = true,
            periodContinueInheritSessionCap = false,
            periodContinueDurationMs = 10 * 60_000L,
        )
        assertFalse(
            LimitGrantDefaults.inheritChipSelected(
                custom.periodContinueInheritSessionCap,
                20 * 60_000L,
            ),
        )
    }

    @Test
    fun `upgrade copies shared difficulty onto all three axes`() {
        val migrated = LimitGrantDefaults.migrateAxisDifficulties(FrictionDifficulty.hard)
        assertEquals(FrictionDifficulty.hard, migrated.open)
        assertEquals(FrictionDifficulty.hard, migrated.period)
        assertEquals(FrictionDifficulty.hard, migrated.session)
    }

    @Test
    fun `upgrade inherit follows existing session cap and never writes 5 minutes`() {
        assertNull(LimitGrantDefaults.migrateSessionRefillOverrideMs())
        assertEquals(
            LimitGrantDefaults.DEFAULT_PERIOD_CONTINUE_MS,
            LimitGrantDefaults.migratePeriodContinueDurationMs(),
        )
        assertNotEquals(5 * 60_000L, LimitGrantDefaults.migratePeriodContinueDurationMs())
        assertTrue(LimitGrantDefaults.migratePeriodContinueInherit(20 * 60_000L))
        assertTrue(LimitGrantDefaults.migratePeriodContinueInherit(null))
        assertTrue(LimitGrantDefaults.migratePeriodContinueInherit(0L))
    }

    @Test
    fun `continue picker is only for period wait math and break`() {
        assertTrue(LimitGrantDefaults.showsPeriodContinue(OnLimitAction.deterrentWait))
        assertTrue(LimitGrantDefaults.showsPeriodContinue(OnLimitAction.deterrentMath))
        assertTrue(LimitGrantDefaults.showsPeriodContinue(OnLimitAction.mandatoryBreak))
        assertFalse(LimitGrantDefaults.showsPeriodContinue(OnLimitAction.notifyOnly))
        assertFalse(LimitGrantDefaults.showsPeriodContinue(OnLimitAction.limitWithExtensions))
        assertFalse(LimitGrantDefaults.showsPeriodContinue(OnLimitAction.hardBlock))
    }

    @Test
    fun `refill picker is only for session wait and math`() {
        assertTrue(LimitGrantDefaults.showsSessionRefill(OnSessionLimitAction.deterrentWait))
        assertTrue(LimitGrantDefaults.showsSessionRefill(OnSessionLimitAction.deterrentMath))
        assertFalse(LimitGrantDefaults.showsSessionRefill(OnSessionLimitAction.notifyOnly))
        assertFalse(LimitGrantDefaults.showsSessionRefill(OnSessionLimitAction.limitWithExtensions))
        assertFalse(LimitGrantDefaults.showsSessionRefill(OnSessionLimitAction.mandatoryBreak))
        assertFalse(LimitGrantDefaults.showsSessionRefill(OnSessionLimitAction.hardBlock))
    }
}
