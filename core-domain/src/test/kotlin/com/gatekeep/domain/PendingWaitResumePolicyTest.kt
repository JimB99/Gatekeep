package com.gatekeep.domain

import com.gatekeep.domain.model.OnOpenAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PendingWaitResumePolicyTest {

    @Test
    fun deterrentWait_resumesOpenGateSurface() {
        assertEquals(
            PendingWaitResumeSurface.OPEN_GATE,
            PendingWaitResumePolicy.surfaceFor(OnOpenAction.deterrentWait),
        )
    }

    @Test
    fun skipResume_whenOpenGateAlreadyPassed() {
        assertEquals(
            false,
            PendingWaitResumePolicy.shouldResume(
                hasPendingWait = true,
                openGateAlreadyPassed = true,
                overlayHasActiveWait = false,
                devicePaused = false,
                screenOn = true,
            ),
        )
    }

    @Test
    fun resume_whenPendingAndStillOnOpenWait() {
        assertEquals(
            true,
            PendingWaitResumePolicy.shouldResume(
                hasPendingWait = true,
                openGateAlreadyPassed = false,
                overlayHasActiveWait = false,
                devicePaused = false,
                screenOn = true,
            ),
        )
    }

    @Test
    fun skipResume_whenDevicePaused() {
        assertEquals(
            false,
            PendingWaitResumePolicy.shouldResume(
                hasPendingWait = true,
                openGateAlreadyPassed = false,
                overlayHasActiveWait = false,
                devicePaused = true,
                screenOn = true,
            ),
        )
    }

    @Test
    fun skipResume_whenOverlayWaitAlreadyRunning() {
        assertEquals(
            false,
            PendingWaitResumePolicy.shouldResume(
                hasPendingWait = true,
                openGateAlreadyPassed = false,
                overlayHasActiveWait = true,
                devicePaused = false,
                screenOn = true,
            ),
        )
    }

    @Test
    fun skipResume_whenOverlayShowInFlight() {
        assertEquals(
            false,
            PendingWaitResumePolicy.shouldResume(
                hasPendingWait = true,
                openGateAlreadyPassed = false,
                overlayHasActiveWait = false,
                devicePaused = false,
                screenOn = true,
                overlayShowInFlight = true,
            ),
        )
    }

    @Test
    fun skipResume_whenScreenOff() {
        assertEquals(
            false,
            PendingWaitResumePolicy.shouldResume(
                hasPendingWait = true,
                openGateAlreadyPassed = false,
                overlayHasActiveWait = false,
                devicePaused = false,
                screenOn = false,
            ),
        )
    }

    @Test
    fun otherOpenActions_resumeSessionLimitSurface() {
        assertEquals(
            PendingWaitResumeSurface.SESSION_LIMIT,
            PendingWaitResumePolicy.surfaceFor(OnOpenAction.none),
        )
    }
}
