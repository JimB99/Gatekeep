package com.gatekeep.domain

object EffectiveLimitDisplay {

  /**
   * Effective cap for UI.
   * With an active grace, the cap is current usage + remaining grace (counts from now).
   * Otherwise it is base + persisted extension bonuses.
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
      val graceCap = graceRemainingMs?.let { remaining -> usageMs + remaining }
      if (graceCap != null) return graceCap
      return baseLimitMs + extensionBonusMs
  }
}
