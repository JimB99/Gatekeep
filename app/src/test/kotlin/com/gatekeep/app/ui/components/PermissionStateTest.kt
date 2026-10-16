package com.gatekeep.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

class PermissionStateTest {

    @Test
    fun allGranted_hidesBanner() {
        val state = PermissionState(
            usageGranted = true,
            accessibilityGranted = true,
            overlayGranted = true,
            lastError = null,
            enforcementEnabled = true,
        )
        assertTrue(state.allGranted)
        assertFalse(state.showBanner)
    }

    @Test
    fun enforcementDisabled_showsBanner() {
        val state = PermissionState(
            usageGranted = true,
            accessibilityGranted = true,
            overlayGranted = true,
            lastError = null,
            enforcementEnabled = false,
        )
        assertTrue(state.allGranted)
        assertTrue(state.showBanner)
    }

    @Test
    fun lastError_showsBannerEvenWhenGranted() {
        val state = PermissionState(
            usageGranted = true,
            accessibilityGranted = true,
            overlayGranted = true,
            lastError = "crash",
            enforcementEnabled = true,
        )
        assertTrue(state.allGranted)
        assertTrue(state.showBanner)
    }

    @Test
    fun accessibilityRevokedBySystem_stillShowsWhenNotGranted() {
        val state = PermissionState(
            usageGranted = true,
            accessibilityGranted = false,
            overlayGranted = true,
            lastError = null,
            enforcementEnabled = true,
            accessibilityRevokedBySystem = true,
        )
        assertFalse(state.allGranted)
        assertTrue(state.showBanner)
    }
}

@RunWith(Parameterized::class)
class PermissionStateMissingFlagTest(
    private val usage: Boolean,
    private val accessibility: Boolean,
    private val overlay: Boolean,
    private val label: String,
) {

    @Test
    fun missingPermission_showsBanner() {
        val state = PermissionState(
            usageGranted = usage,
            accessibilityGranted = accessibility,
            overlayGranted = overlay,
            lastError = null,
            enforcementEnabled = true,
        )
        assertFalse(label, state.allGranted)
        assertTrue(label, state.showBanner)
        assertEquals(usage, state.usageGranted)
        assertEquals(accessibility, state.accessibilityGranted)
        assertEquals(overlay, state.overlayGranted)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{3}")
        fun cases(): List<Array<Any>> = listOf(
            arrayOf(false, true, true, "missing usage"),
            arrayOf(true, false, true, "missing accessibility"),
            arrayOf(true, true, false, "missing overlay"),
            arrayOf(false, false, true, "missing usage and accessibility"),
        )
    }
}
