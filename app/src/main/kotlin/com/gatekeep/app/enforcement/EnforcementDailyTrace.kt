package com.gatekeep.app.enforcement

/**
 * Daily used / cap / remaining on evaluate and overlay_show traces.
 * A daily-limit overlay is only valid when remaining is present and ≤ 0.
 */
object EnforcementDailyTrace {
    const val USED = "dailyUsedMs"
    const val CAP = "dailyCapMs"
    const val REMAINING = "dailyRemainingMs"

    fun fields(usedMs: Long?, capMs: Long?, remainingMs: Long?): Map<String, String> {
        val out = linkedMapOf<String, String>()
        if (usedMs != null) out[USED] = usedMs.toString()
        if (capMs != null) out[CAP] = capMs.toString()
        if (remainingMs != null) out[REMAINING] = remainingMs.toString()
        return out
    }

    /** Skip only when remaining is known and still positive. Unknown remaining trusts RuleEngine. */
    fun shouldPresentDailyLimit(remainingMs: Long?): Boolean =
        remainingMs == null || remainingMs <= 0L
}
