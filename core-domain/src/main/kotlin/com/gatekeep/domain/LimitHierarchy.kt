package com.gatekeep.domain

/**
 * Validates time-limit ordering: weekly >= daily >= hourly >= session.
 * A value of 0 means that tier is off and is skipped in comparisons.
 */
enum class LimitField {
    Weekly,
    Daily,
    Hourly,
    Session,
}

data class LimitHierarchyValidation(
    val valid: Boolean,
    val invalidFields: Set<LimitField> = emptySet(),
)

object LimitHierarchy {
    fun validate(
        weeklyMs: Long,
        dailyMs: Long,
        hourlyMs: Long,
        sessionMs: Long = 0L,
    ): LimitHierarchyValidation {
        val values = mapOf(
            LimitField.Weekly to weeklyMs,
            LimitField.Daily to dailyMs,
            LimitField.Hourly to hourlyMs,
            LimitField.Session to sessionMs,
        )
        val order = listOf(
            LimitField.Weekly,
            LimitField.Daily,
            LimitField.Hourly,
            LimitField.Session,
        )
        val invalid = mutableSetOf<LimitField>()
        var previous: Pair<LimitField, Long>? = null
        for (field in order) {
            val value = values.getValue(field)
            if (value <= 0) continue
            if (previous != null && previous.second < value) {
                invalid += previous.first
                invalid += field
            }
            previous = field to value
        }
        return LimitHierarchyValidation(
            valid = invalid.isEmpty(),
            invalidFields = invalid,
        )
    }

    fun isValid(weeklyMs: Long, dailyMs: Long, hourlyMs: Long, sessionMs: Long = 0L): Boolean =
        validate(weeklyMs, dailyMs, hourlyMs, sessionMs).valid
}
