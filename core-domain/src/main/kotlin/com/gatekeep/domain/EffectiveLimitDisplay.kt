package com.gatekeep.domain

object EffectiveLimitDisplay {

  /**
   * Effective cap for UI: base + persisted bonuses, or usage + active grace (counts from now).
   * Returns null when unlimited (no limit today) or when no base limit is configured.
   */
  fun effectiveLimitMs(
      baseLimitMs: Long?,
      usageMs: Long,
      extensionBonusMs: Long,
      graceRemainingMs: Long?,
      noLimitToday: Boolean,
  ): Long? {
      if (baseLimitMs == null) return null
      if (noLimitToday) return null
      val bonusCap = baseLimitMs + extensionBonusMs
      val graceCap = graceRemainingMs?.let { usageMs + it }
      return when {
          graceCap != null -> maxOf(bonusCap, graceCap)
          else -> bonusCap
      }
  }
}
