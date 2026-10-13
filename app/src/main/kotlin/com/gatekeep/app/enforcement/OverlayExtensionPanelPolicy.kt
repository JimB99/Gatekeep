package com.gatekeep.app.enforcement

/**
 * Overlay extension chrome: quota copy and “no limit today” stay visible even when
 * every +minute option would be denied by daily or consecutive quota.
 */
object OverlayExtensionPanelPolicy {

    data class Layout(
        val showPanel: Boolean,
        val minuteOptions: List<Int>,
        val showNoLimitToday: Boolean,
    )

    fun layout(request: BlockOverlayRequest): Layout {
        if (!request.bypassAllowed || !request.useExtensionButtons) {
            return Layout(
                showPanel = false,
                minuteOptions = emptyList(),
                showNoLimitToday = false,
            )
        }
        return Layout(
            showPanel = true,
            minuteOptions = request.extensionOptionMinutes,
            showNoLimitToday = request.showNoLimitToday,
        )
    }
}
