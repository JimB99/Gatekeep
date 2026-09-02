package com.gatekeep.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class WheelCenterSelectorTest {

    @Test
    fun `selects the item whose center is closest to the viewport middle`() {
        val items = listOf(
            WheelCenterSelector.Item(index = 0, offset = 0, size = 40),
            WheelCenterSelector.Item(index = 1, offset = 40, size = 40),
            WheelCenterSelector.Item(index = 2, offset = 80, size = 40),
            WheelCenterSelector.Item(index = 3, offset = 120, size = 40),
            WheelCenterSelector.Item(index = 4, offset = 160, size = 40),
        )
        assertEquals(2, WheelCenterSelector.selectedIndex(items, viewportStart = 0, viewportEnd = 200))
    }

    @Test
    fun `selects padded first item when it sits in the center`() {
        val items = listOf(
            WheelCenterSelector.Item(index = 5, offset = 80, size = 40),
            WheelCenterSelector.Item(index = 6, offset = 120, size = 40),
        )
        assertEquals(5, WheelCenterSelector.selectedIndex(items, viewportStart = 0, viewportEnd = 200))
    }
}
