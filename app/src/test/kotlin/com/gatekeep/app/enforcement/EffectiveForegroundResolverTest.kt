package com.gatekeep.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Test

class EffectiveForegroundResolverTest {

    private val blocked = "com.blocked.app"
    private val launcher = "com.android.launcher3"

    @Test
    fun resolve_nullAccessibility_usesUsageStats() {
        assertEquals(
            launcher,
            EffectiveForegroundResolver.resolve(
                accessibilityForeground = null,
                usageStatsForeground = launcher,
                blockedPackage = blocked,
                presentation = BlockPresentation.None,
            ),
        )
    }

    @Test
    fun resolve_visibleBlock_usageStillLauncher_treatsAsLeft() {
        assertEquals(
            launcher,
            EffectiveForegroundResolver.resolve(
                accessibilityForeground = blocked,
                usageStatsForeground = launcher,
                blockedPackage = blocked,
                presentation = BlockPresentation.Visible(blocked, 1L),
            ),
        )
    }

    @Test
    fun resolve_hiddenForOtherApp_returnToBlocked_trustsAccessibility() {
        assertEquals(
            blocked,
            EffectiveForegroundResolver.resolve(
                accessibilityForeground = blocked,
                usageStatsForeground = launcher,
                blockedPackage = blocked,
                presentation = BlockPresentation.HiddenForOtherApp(blocked, 1L),
            ),
        )
    }

    @Test
    fun resolve_notBlocked_usesAccessibility() {
        assertEquals(
            blocked,
            EffectiveForegroundResolver.resolve(
                accessibilityForeground = blocked,
                usageStatsForeground = launcher,
                blockedPackage = null,
                presentation = BlockPresentation.Visible(blocked, 1L),
            ),
        )
    }
}
