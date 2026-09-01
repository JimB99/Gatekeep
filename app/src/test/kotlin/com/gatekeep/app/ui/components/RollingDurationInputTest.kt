package com.gatekeep.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RollingDurationInputTest {

    @Test
    fun `starts at zero state`() {
        val input = RollingDurationInput()
        assertEquals("00:00:00", input.formatted())
        assertEquals(0, input.totalSeconds())
    }

    @Test
    fun `digit entry shifts from right to left`() {
        var input = RollingDurationInput()
        input = input.insertDigit(3)
        assertEquals("00:00:03", input.formatted())
        input = input.insertDigit(0)
        assertEquals("00:00:30", input.formatted())
        input = input.insertDigit(0)
        assertEquals("00:03:00", input.formatted())
        input = input.insertDigit(0)
        assertEquals("00:30:00", input.formatted())
        assertEquals(30 * 60, input.totalSeconds())
    }

    @Test
    fun `double zero appends two zeros atomically`() {
        var input = RollingDurationInput()
        input = input.insertDigit(3)
        input = input.insertDigit(0)
        assertEquals("00:00:30", input.formatted())
        input = input.insertDoubleZero()
        assertEquals("00:30:00", input.formatted())
        assertEquals(30 * 60, input.totalSeconds())
    }

    @Test
    fun `backspace shifts digits right`() {
        var input = RollingDurationInput.fromTotalSeconds(30 * 60)
        assertEquals("00:30:00", input.formatted())
        input = input.backspace()
        assertEquals("00:03:00", input.formatted())
        input = input.backspace()
        assertEquals("00:00:30", input.formatted())
    }

    @Test
    fun `clear resets to zero`() {
        val input = RollingDurationInput.fromTotalSeconds(3661).clear()
        assertEquals("00:00:00", input.formatted())
    }

    @Test
    fun `initializes from existing seconds value`() {
        val input = RollingDurationInput.fromTotalSeconds(3661)
        assertEquals("01:01:01", input.formatted())
        assertEquals(3661, input.totalSeconds())
    }

    @Test
    fun `initializes from existing milliseconds value`() {
        val input = RollingDurationInput.fromTotalMilliseconds(90_500L)
        assertEquals("00:01:30", input.formatted())
        assertEquals(90_000L, input.totalMilliseconds())
    }

    @Test
    fun `rejects invalid minute and second fields`() {
        val input = RollingDurationInput("006099")
        assertFalse(input.hasValidMinuteAndSecondFields())
        assertEquals(DurationValidationResult.InvalidMinuteOrSecond, input.validate())
    }

    @Test
    fun `validates maximum bound`() {
        val input = RollingDurationInput.fromTotalSeconds(3600)
        assertEquals(
            DurationValidationResult.ExceedsMaximum(3540),
            input.validate(maxTotalSeconds = 3540),
        )
        assertEquals(DurationValidationResult.Valid, input.validate(maxTotalSeconds = 3600))
    }

    @Test
    fun `preserves exact second conversion`() {
        val input = RollingDurationInput.fromTotalSeconds(3723)
        assertEquals(3723, input.totalSeconds())
        assertEquals(3_723_000L, input.totalMilliseconds())
    }
}
