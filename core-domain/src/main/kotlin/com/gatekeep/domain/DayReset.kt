package com.gatekeep.domain

object DayReset {
    const val DEFAULT_MINUTE_OF_DAY = 4 * 60
    const val MINUTE_OF_DAY_MIN = 0
    const val MINUTE_OF_DAY_MAX = 24 * 60 - 1

    fun coerce(minuteOfDay: Int): Int = minuteOfDay.coerceIn(MINUTE_OF_DAY_MIN, MINUTE_OF_DAY_MAX)
}
