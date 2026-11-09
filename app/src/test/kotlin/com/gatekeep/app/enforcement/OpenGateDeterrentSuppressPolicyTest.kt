package com.gatekeep.app.enforcement

import com.gatekeep.domain.SessionTracker
import com.gatekeep.domain.model.BlockPresentationReason
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenGateDeterrentSuppressPolicyTest {

    private val pkg = "app.revanced.android.youtube"

    @Test
    fun overlayRestore_afterHome_skipsNewOpenGateWhenPosted() {
        val session = SessionTracker.startSession(pkg, 1_000L)
        val lastPosted = OverlayPresentPolicy.Identity(
            packageName = pkg,
            reason = BlockPresentationReason.openGate,
            isOpenGate = true,
        )
        assertTrue(
            OpenGateDeterrentSuppressPolicy.shouldReshowInsteadOfNewDeterrent(
                packageName = pkg,
                session = session,
                nowEpochMs = 1_800L,
                lastPosted = lastPosted,
                isBlockingActive = true,
                blockedPackage = pkg,
            ),
        )
    }

    @Test
    fun firstOpenGate_noLastPosted_allowsNewDeterrent() {
        val session = SessionTracker.startSession(pkg, 1_000L)
        assertFalse(
            OpenGateDeterrentSuppressPolicy.shouldReshowInsteadOfNewDeterrent(
                packageName = pkg,
                session = session,
                nowEpochMs = 1_100L,
                lastPosted = null,
                isBlockingActive = false,
                blockedPackage = null,
            ),
        )
    }

    @Test
    fun limitBlockPosted_doesNotSuppressOpenGate() {
        val session = SessionTracker.startSession(pkg, 1_000L)
            .copy(frictionStartedAtEpochMs = 1_200L)
        val limitPosted = OverlayPresentPolicy.Identity(
            packageName = pkg,
            reason = BlockPresentationReason.sessionLimit,
            isOpenGate = false,
        )
        assertFalse(
            OpenGateDeterrentSuppressPolicy.shouldReshowInsteadOfNewDeterrent(
                packageName = pkg,
                session = session,
                nowEpochMs = 1_500L,
                lastPosted = limitPosted,
                isBlockingActive = true,
                blockedPackage = pkg,
            ),
        )
    }
}
