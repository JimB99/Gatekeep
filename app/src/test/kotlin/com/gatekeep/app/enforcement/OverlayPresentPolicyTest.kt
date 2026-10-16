package com.gatekeep.app.enforcement

import com.gatekeep.domain.model.BlockPresentationReason
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Repeated evaluate() was posting overlay_show sessionLimit several times a second.
 * Show once until the overlay is actually hidden or the request identity changes.
 */
class OverlayPresentPolicyTest {

    private val sessionLimit = OverlayPresentPolicy.Identity(
        packageName = "app.revanced.android.youtube",
        reason = BlockPresentationReason.sessionLimit,
        isOpenGate = false,
    )
    private val openGate = OverlayPresentPolicy.Identity(
        packageName = "app.revanced.android.youtube",
        reason = BlockPresentationReason.openGate,
        isOpenGate = true,
    )

    @Test
    fun firstShow_alwaysPosts() {
        assertTrue(
            OverlayPresentPolicy.shouldPost(
                incoming = sessionLimit,
                lastPosted = null,
                overlayVisible = false,
                showInFlight = false,
            ),
        )
    }

    @Test
    fun sameSessionLimit_whileVisible_doesNotRepost() {
        assertFalse(
            OverlayPresentPolicy.shouldPost(
                incoming = sessionLimit,
                lastPosted = sessionLimit,
                overlayVisible = true,
                showInFlight = false,
            ),
        )
    }

    @Test
    fun sameSessionLimit_whileShowInFlight_doesNotRepost() {
        assertFalse(
            OverlayPresentPolicy.shouldPost(
                incoming = sessionLimit,
                lastPosted = sessionLimit,
                overlayVisible = false,
                showInFlight = true,
            ),
        )
    }

    @Test
    fun sameSessionLimit_afterHide_reposts() {
        assertTrue(
            OverlayPresentPolicy.shouldPost(
                incoming = sessionLimit,
                lastPosted = sessionLimit,
                overlayVisible = false,
                showInFlight = false,
            ),
        )
    }

    @Test
    fun openGateThenSessionLimit_postsBoth() {
        assertTrue(
            OverlayPresentPolicy.shouldPost(
                incoming = sessionLimit,
                lastPosted = openGate,
                overlayVisible = true,
                showInFlight = false,
            ),
        )
    }

    @Test
    fun shouldReshow_onlyWhenHiddenAndIdle() {
        assertFalse(OverlayPresentPolicy.shouldReshow(overlayVisible = true, showInFlight = false))
        assertFalse(OverlayPresentPolicy.shouldReshow(overlayVisible = false, showInFlight = true))
        assertTrue(OverlayPresentPolicy.shouldReshow(overlayVisible = false, showInFlight = false))
    }

    @Test
    fun postedDelayOpen_doesNotRunAfterHideEpochBump() {
        assertTrue(OverlayPresentPolicy.shouldRunPostedShow(postedEpoch = 4L, currentEpoch = 4L))
        assertFalse(OverlayPresentPolicy.shouldRunPostedShow(postedEpoch = 4L, currentEpoch = 5L))
    }

    @Test
    fun staleWaitTick_removesOnlyWhileItIsTheActiveRunnable() {
        assertTrue(OverlayPresentPolicy.shouldRemoveOnStaleWaitTick(isCurrentWaitRunnable = true))
        assertFalse(OverlayPresentPolicy.shouldRemoveOnStaleWaitTick(isCurrentWaitRunnable = false))
    }
}
