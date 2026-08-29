package com.gatekeep.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockPresentationReducerTest {

    @Test
    fun `block entered transitions to visible with incremented generation`() {
        val initial = BlockPresentationState()
        val next = BlockPresentationReducer.onBlockEntered(initial, "com.blocked")

        assertEquals(BlockPresentation.Visible("com.blocked", 1L), next.presentation)
        assertEquals(1L, next.generation)
        assertTrue(BlockPresentationReducer.isBlockingActive(next))
        assertEquals("com.blocked", BlockPresentationReducer.blockedPackage(next))
    }

    @Test
    fun `gatekeep overlay event is ignored while overlay visible and blocking`() {
        val state = BlockPresentationReducer.onBlockEntered(BlockPresentationState(), "com.blocked")

        assertTrue(
            BlockPresentationReducer.shouldIgnoreGatekeepForegroundEvent(
                state = state,
                overlayVisible = true,
                windowClassName = "android.widget.FrameLayout",
            ),
        )
    }

    @Test
    fun `gatekeep main activity foreground dismisses even while overlay visible`() {
        val state = BlockPresentationReducer.onBlockEntered(BlockPresentationState(), "com.blocked")

        assertFalse(
            BlockPresentationReducer.shouldIgnoreGatekeepForegroundEvent(
                state = state,
                overlayVisible = true,
                windowClassName = "com.gatekeep.app.MainActivity",
            ),
        )
        assertTrue(BlockPresentationReducer.isGatekeepActivityWindow("com.gatekeep.app.MainActivity"))
    }

    @Test
    fun `genuine gatekeep open is not ignored without visible overlay`() {
        val state = BlockPresentationReducer.onBlockEntered(BlockPresentationState(), "com.blocked")

        assertFalse(
            BlockPresentationReducer.shouldIgnoreGatekeepForegroundEvent(
                state = state,
                overlayVisible = false,
                windowClassName = "com.gatekeep.app.MainActivity",
            ),
        )
    }

    @Test
    fun `non profile foreground hides visible block without clearing intent`() {
        val visible = BlockPresentationReducer.onBlockEntered(BlockPresentationState(), "com.blocked")
        val hidden = BlockPresentationReducer.onHideForOtherApp(visible)

        assertEquals(
            BlockPresentation.HiddenForOtherApp("com.blocked", visible.generation),
            hidden.presentation,
        )
        assertTrue(BlockPresentationReducer.isBlockingActive(hidden))
    }

    @Test
    fun `returning to blocked app restores visible presentation`() {
        val visible = BlockPresentationReducer.onBlockEntered(BlockPresentationState(), "com.blocked")
        val hidden = BlockPresentationReducer.onHideForOtherApp(visible)
        val restored = BlockPresentationReducer.onReturnToBlockedApp(hidden, "com.blocked")

        assertEquals(
            BlockPresentation.Visible("com.blocked", visible.generation),
            restored.presentation,
        )
    }

    @Test
    fun `gatekeep activity foreground clears block state`() {
        val cleared = BlockPresentationReducer.onBlockCleared(
            BlockPresentationReducer.onBlockEntered(BlockPresentationState(), "com.blocked"),
        )

        assertEquals(BlockPresentation.None, cleared.presentation)
        assertFalse(BlockPresentationReducer.isBlockingActive(cleared))
    }

    @Test
    fun `notify-only allowed clears when no active block`() {
        val token = EvaluationToken("com.app", 0L)

        assertTrue(
            BlockPresentationReducer.shouldApplyAllowedClear(
                state = BlockPresentationState(),
                token = token,
                currentForegroundPackage = "com.app",
            ),
        )
    }

    @Test
    fun `stale allowed from previous generation does not clear active block`() {
        val visible = BlockPresentationReducer.onBlockEntered(BlockPresentationState(), "com.blocked")
        val staleToken = EvaluationToken("com.blocked", visible.generation - 1)

        assertFalse(
            BlockPresentationReducer.shouldApplyAllowedClear(
                state = visible,
                token = staleToken,
                currentForegroundPackage = "com.blocked",
            ),
        )
    }

    @Test
    fun `allowed for different foreground package is discarded`() {
        val visible = BlockPresentationReducer.onBlockEntered(BlockPresentationState(), "com.blocked")
        val token = EvaluationToken("com.blocked", visible.generation)

        assertFalse(
            BlockPresentationReducer.shouldApplyAllowedClear(
                state = visible,
                token = token,
                currentForegroundPackage = "com.launcher",
            ),
        )
    }

    @Test
    fun `authoritative allowed clears active visible block`() {
        val visible = BlockPresentationReducer.onBlockEntered(BlockPresentationState(), "com.blocked")
        val token = EvaluationToken("com.blocked", visible.generation)

        assertTrue(
            BlockPresentationReducer.shouldApplyAllowedClear(
                state = visible,
                token = token,
                currentForegroundPackage = "com.blocked",
            ),
        )
    }

    @Test
    fun `hidden for other app keeps block intent across temporary hide`() {
        val visible = BlockPresentationReducer.onBlockEntered(BlockPresentationState(), "com.blocked")
        val hidden = BlockPresentationReducer.onHideForOtherApp(visible)

        assertEquals("com.blocked", BlockPresentationReducer.blockedPackage(hidden))
        assertTrue(hidden.presentation is BlockPresentation.HiddenForOtherApp)
    }
}
