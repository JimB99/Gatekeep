package com.gatekeep.app.enforcement

import com.gatekeep.domain.EffectiveLimitDisplay
import com.gatekeep.domain.ExtensionPeriodDisplay

object UsageDisplayLimits {

    fun displayLimitMs(
        baseLimitMs: Long?,
        extension: ExtensionPeriodDisplay,
        noLimitToday: Boolean,
        periodMs: Long,
    ): Long? = EffectiveLimitDisplay.displayLimitMs(
        baseLimitMs = baseLimitMs,
        extensionBonusMs = extension.bonusMs,
        extensionAnchorMs = extension.anchorMs,
        noLimitToday = noLimitToday,
        periodMs = periodMs,
    )
}
