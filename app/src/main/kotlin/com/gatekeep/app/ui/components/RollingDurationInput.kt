package com.gatekeep.app.ui.components

/**
 * Google Clock timer-style rolling six-digit HH:MM:SS entry.
 * Digits shift left as new digits are entered from the right.
 */
data class RollingDurationInput(
    private val digits: String = "000000",
) {
    companion object {
        const val DIGIT_COUNT = 6
        const val MAX_TOTAL_SECONDS = 99 * 3600 + 59 * 60 + 59

        fun fromTotalSeconds(totalSeconds: Int): RollingDurationInput {
            val clamped = totalSeconds.coerceIn(0, MAX_TOTAL_SECONDS)
            val h = clamped / 3600
            val m = (clamped % 3600) / 60
            val s = clamped % 60
            return RollingDurationInput("%02d%02d%02d".format(h, m, s))
        }

        fun fromTotalMilliseconds(totalMs: Long): RollingDurationInput =
            fromTotalSeconds((totalMs / 1000).toInt())
    }

    fun formatted(): String {
        val h = digits.substring(0, 2)
        val m = digits.substring(2, 4)
        val s = digits.substring(4, 6)
        return "$h:$m:$s"
    }

    fun totalSeconds(): Int {
        val h = digits.substring(0, 2).toIntOrNull() ?: 0
        val m = digits.substring(2, 4).toIntOrNull() ?: 0
        val s = digits.substring(4, 6).toIntOrNull() ?: 0
        return h * 3600 + m * 60 + s
    }

    fun totalMilliseconds(): Long = totalSeconds().toLong() * 1000L

    fun insertDigit(digit: Int): RollingDurationInput {
        val d = digit.coerceIn(0, 9)
        val shifted = digits.drop(1) + d
        return RollingDurationInput(shifted)
    }

    fun insertDoubleZero(): RollingDurationInput {
        val shifted = digits.drop(2) + "00"
        return RollingDurationInput(shifted)
    }

    fun backspace(): RollingDurationInput {
        val shifted = "0" + digits.dropLast(1)
        return RollingDurationInput(shifted)
    }

    fun clear(): RollingDurationInput = RollingDurationInput()

    fun hasValidMinuteAndSecondFields(): Boolean {
        val m = digits.substring(2, 4).toIntOrNull() ?: 0
        val s = digits.substring(4, 6).toIntOrNull() ?: 0
        return m in 0..59 && s in 0..59
    }

    fun validate(maxTotalSeconds: Int? = null): DurationValidationResult {
        if (!hasValidMinuteAndSecondFields()) {
            return DurationValidationResult.InvalidMinuteOrSecond
        }
        val total = totalSeconds()
        if (maxTotalSeconds != null && total > maxTotalSeconds) {
            return DurationValidationResult.ExceedsMaximum(maxTotalSeconds)
        }
        return DurationValidationResult.Valid
    }
}

sealed interface DurationValidationResult {
    data object Valid : DurationValidationResult
    data object InvalidMinuteOrSecond : DurationValidationResult
    data class ExceedsMaximum(val maxTotalSeconds: Int) : DurationValidationResult
}

fun formatDurationDisplaySeconds(totalSeconds: Int): String {
    val clamped = totalSeconds.coerceAtLeast(0)
    val h = clamped / 3600
    val m = (clamped % 3600) / 60
    val s = clamped % 60
    return when {
        h > 0 -> "${h}h ${m}m ${s}s"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

fun formatDurationDisplayMilliseconds(totalMs: Long): String =
    formatDurationDisplaySeconds((totalMs / 1000).toInt())
