package com.gatekeep.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceUsePolicyTest {

    @Test
    fun screenOff_isPaused() {
        assertEquals(
            DeviceUsePhase.Paused,
            DeviceUsePolicy.phase(screenOn = false, keyguardLocked = false, hasLeftApp = false),
        )
    }

    @Test
    fun screenOnWithKeyguard_isPaused() {
        assertEquals(
            DeviceUsePhase.Paused,
            DeviceUsePolicy.phase(screenOn = true, keyguardLocked = true, hasLeftApp = false),
        )
    }

    @Test
    fun unlock_isUsing() {
        assertEquals(
            DeviceUsePhase.Using,
            DeviceUsePolicy.phase(screenOn = true, keyguardLocked = false, hasLeftApp = false),
        )
    }

    @Test
    fun leaveWinsOverPause() {
        assertEquals(
            DeviceUsePhase.Left,
            DeviceUsePolicy.phase(screenOn = false, keyguardLocked = true, hasLeftApp = true),
        )
    }

    @Test
    fun evaluateOnResume_onlyPausedToUsing() {
        assertTrue(
            DeviceUsePolicy.shouldEvaluateOnResume(DeviceUsePhase.Paused, DeviceUsePhase.Using),
        )
        assertFalse(
            DeviceUsePolicy.shouldEvaluateOnResume(DeviceUsePhase.Paused, DeviceUsePhase.Paused),
        )
        assertFalse(
            DeviceUsePolicy.shouldEvaluateOnResume(DeviceUsePhase.Using, DeviceUsePhase.Using),
        )
        assertFalse(
            DeviceUsePolicy.shouldEvaluateOnResume(DeviceUsePhase.Left, DeviceUsePhase.Using),
        )
    }

    @Test
    fun screenOnWhileLocked_doesNotResumeEvaluate() {
        val previous = DeviceUsePolicy.phase(
            screenOn = false, keyguardLocked = true, hasLeftApp = false,
        )
        val screenOnLocked = DeviceUsePolicy.phase(
            screenOn = true, keyguardLocked = true, hasLeftApp = false,
        )
        assertEquals(DeviceUsePhase.Paused, previous)
        assertEquals(DeviceUsePhase.Paused, screenOnLocked)
        assertFalse(DeviceUsePolicy.shouldEvaluateOnResume(previous, screenOnLocked))
        val unlocked = DeviceUsePolicy.phase(
            screenOn = true, keyguardLocked = false, hasLeftApp = false,
        )
        assertTrue(DeviceUsePolicy.shouldEvaluateOnResume(screenOnLocked, unlocked))
    }

    @Test
    fun freezeClock_whenPausedOrLeft() {
        assertTrue(DeviceUsePolicy.shouldFreezeSessionClock(DeviceUsePhase.Paused))
        assertTrue(DeviceUsePolicy.shouldFreezeSessionClock(DeviceUsePhase.Left))
        assertFalse(DeviceUsePolicy.shouldFreezeSessionClock(DeviceUsePhase.Using))
    }
}
