package com.gatekeep.app.enforcement

import com.gatekeep.domain.model.BlockPresentationReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayExtensionPanelPolicyTest {

    @Test
    fun exhaustedQuota_keepsPanelQuotaAndNoLimit_hidesMinuteButtons() {
        val layout = OverlayExtensionPanelPolicy.layout(
            request(
                useExtensionButtons = true,
                extensionOptionMinutes = emptyList(),
                showNoLimitToday = true,
            ),
        )

        assertTrue(layout.showPanel)
        assertTrue(layout.showNoLimitToday)
        assertEquals(emptyList<Int>(), layout.minuteOptions)
    }

    @Test
    fun remainingQuota_keepsMinuteButtons() {
        val layout = OverlayExtensionPanelPolicy.layout(
            request(
                useExtensionButtons = true,
                extensionOptionMinutes = listOf(1, 5, 10),
                showNoLimitToday = true,
            ),
        )

        assertTrue(layout.showPanel)
        assertEquals(listOf(1, 5, 10), layout.minuteOptions)
        assertTrue(layout.showNoLimitToday)
    }

    @Test
    fun exhaustedQuota_doesNotForceNoLimitWhenPolicyHidesIt() {
        val layout = OverlayExtensionPanelPolicy.layout(
            request(
                useExtensionButtons = true,
                extensionOptionMinutes = emptyList(),
                showNoLimitToday = false,
            ),
        )

        assertTrue(layout.showPanel)
        assertFalse(layout.showNoLimitToday)
    }

    @Test
    fun bypassNotAllowed_hidesPanel() {
        val layout = OverlayExtensionPanelPolicy.layout(
            request(
                useExtensionButtons = true,
                extensionOptionMinutes = listOf(5),
                showNoLimitToday = true,
            ).copy(bypassAllowed = false),
        )

        assertFalse(layout.showPanel)
        assertFalse(layout.showNoLimitToday)
        assertEquals(emptyList<Int>(), layout.minuteOptions)
    }

    @Test
    fun extensionsDisabled_hidesPanel() {
        val layout = OverlayExtensionPanelPolicy.layout(
            request(
                useExtensionButtons = false,
                extensionOptionMinutes = listOf(5),
                showNoLimitToday = true,
            ),
        )

        assertFalse(layout.showPanel)
        assertFalse(layout.showNoLimitToday)
        assertEquals(emptyList<Int>(), layout.minuteOptions)
    }

    private fun request(
        useExtensionButtons: Boolean,
        extensionOptionMinutes: List<Int>,
        showNoLimitToday: Boolean,
    ) = BlockOverlayRequest(
        packageName = "com.example.app",
        message = "Daily limit reached",
        reason = BlockPresentationReason.dailyLimit,
        bypassAllowed = true,
        extensionOptionMinutes = extensionOptionMinutes,
        showNoLimitToday = showNoLimitToday,
        useExtensionButtons = useExtensionButtons,
    )
}
