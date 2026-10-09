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
    fun otherOpenActions_resumeSessionLimitSurface() {
        assertEquals(
            PendingWaitResumeSurface.SESSION_LIMIT,
            PendingWaitResumePolicy.surfaceFor(OnOpenAction.none),
        )
    }
}
