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
    Schedule,
    Focus,
    Break,
    Extension,
    ProfilePin,
    Blocked,
}

data class OverlayChrome(
    val accent: OverlayAccentFamily,
    val title: OverlayChromeTitle,
)

/**
 * Overlay frame: [OverlayChromeTitle] is the short category on [overlay_title];
 * body copy comes from [com.gatekeep.app.util.BlockMessageResolver.blockMessage].
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
        BlockPresentationReason.scheduleBlock,
        BlockPresentationReason.outsideSchedule,
        -> OverlayChrome(OverlayAccentFamily.Default, OverlayChromeTitle.Schedule)
        BlockPresentationReason.focusMode -> OverlayChrome(
            OverlayAccentFamily.Default,
            OverlayChromeTitle.Focus,
        )
        BlockPresentationReason.onBreak -> OverlayChrome(
            OverlayAccentFamily.Default,
            OverlayChromeTitle.Break,
        )
        BlockPresentationReason.extensionDenied -> OverlayChrome(
            OverlayAccentFamily.Default,
            OverlayChromeTitle.Extension,
        )
        BlockPresentationReason.profilePin -> OverlayChrome(
            OverlayAccentFamily.Default,
            OverlayChromeTitle.ProfilePin,
        )
        BlockPresentationReason.notMonitored,
        BlockPresentationReason.profilePaused,
        BlockPresentationReason.appPaused,
        BlockPresentationReason.unknown,
        -> OverlayChrome(OverlayAccentFamily.Default, OverlayChromeTitle.Blocked)
    }
}
