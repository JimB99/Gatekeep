package com.gatekeep.app.enforcement

import com.gatekeep.domain.model.BlockPresentationReason

enum class OverlayAccentFamily {
    OnOpen,
    Session,
    Period,
    Default,
}

enum class OverlayChromeTitle {
    OnOpenWait,
    SessionLimit,
    DailyLimit,
    HourlyLimit,
    WeeklyLimit,
    ReasonLabel,
}

data class OverlayChrome(
    val accent: OverlayAccentFamily,
    val title: OverlayChromeTitle,
)

/**
 * One overlay frame: title names the reason, accent groups on-open / session / period.
 * Daily, hourly, and weekly share the period look.
 */
object OverlayChromePolicy {

    fun chrome(reason: BlockPresentationReason): OverlayChrome = when (reason) {
        BlockPresentationReason.openGate -> OverlayChrome(
            OverlayAccentFamily.OnOpen,
            OverlayChromeTitle.OnOpenWait,
        )
        BlockPresentationReason.sessionLimit -> OverlayChrome(
            OverlayAccentFamily.Session,
            OverlayChromeTitle.SessionLimit,
        )
        BlockPresentationReason.dailyLimit -> OverlayChrome(
            OverlayAccentFamily.Period,
            OverlayChromeTitle.DailyLimit,
        )
        BlockPresentationReason.hourlyLimit -> OverlayChrome(
            OverlayAccentFamily.Period,
            OverlayChromeTitle.HourlyLimit,
        )
        BlockPresentationReason.weeklyLimit -> OverlayChrome(
            OverlayAccentFamily.Period,
            OverlayChromeTitle.WeeklyLimit,
        )
        else -> OverlayChrome(
            OverlayAccentFamily.Default,
            OverlayChromeTitle.ReasonLabel,
        )
    }
}
