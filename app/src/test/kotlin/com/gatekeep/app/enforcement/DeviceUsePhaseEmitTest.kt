package com.gatekeep.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceUsePhaseEmitTest {

    @Test
    fun samePhase_doesNotNotify() {
        assertNull(
            DeviceUsePhaseEmit.notify(
                previous = DeviceUsePhase.Paused,
                next = DeviceUsePhase.Paused,
            ),
        )
    }

    @Test
    fun usingToPaused_notifiesPaused() {
        assertEquals(
            DeviceUsePhaseEmit.Notify.Paused,
            DeviceUsePhaseEmit.notify(
                previous = DeviceUsePhase.Using,
                next = DeviceUsePhase.Paused,
            ),
        )
    }

    @Test
    fun pausedToUsing_notifiesUsing() {
        assertEquals(
            DeviceUsePhaseEmit.Notify.BecameUsing,
            DeviceUsePhaseEmit.notify(
                previous = DeviceUsePhase.Paused,
                next = DeviceUsePhase.Using,
            ),
        )
    }
}
