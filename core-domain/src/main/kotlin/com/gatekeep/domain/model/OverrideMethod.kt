package com.gatekeep.domain.model

enum class OverrideMethod(val storageValue: String) {
    extension("extension"),
    noLimitToday("noLimitToday"),
    unknown("unknown"),
    ;

    companion object {
        fun fromStorage(value: String?): OverrideMethod =
            entries.find { it.storageValue == value } ?: unknown
    }
}

enum class BlockPresentationReason(val storageValue: String) {
    openGate("openGate"),
    profilePin("profilePin"),
    extensionDenied("extensionDenied"),
    notMonitored("notMonitored"),
    profilePaused("profilePaused"),
    appPaused("appPaused"),
    outsideSchedule("outsideSchedule"),
    scheduleBlock("scheduleBlock"),
    hourlyLimit("hourlyLimit"),
    dailyLimit("dailyLimit"),
    weeklyLimit("weeklyLimit"),
    sessionLimit("sessionLimit"),
    onBreak("onBreak"),
    focusMode("focusMode"),
    unknown("unknown"),
    ;

    val allowsExtensionButtons: Boolean
        get() = this !in setOf(scheduleBlock, extensionDenied, focusMode, outsideSchedule)

    val isOpenGateFlow: Boolean
        get() = this == openGate

    companion object {
        fun fromStorage(value: String?): BlockPresentationReason =
            entries.find { it.storageValue == value } ?: unknown

        fun fromBlockReason(reason: BlockReason): BlockPresentationReason =
            entries.find { it.storageValue == reason.name } ?: unknown
    }
}
