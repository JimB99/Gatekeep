package com.gatekeep.app.enforcement

import com.gatekeep.app.util.ForegroundEventType

/**
 * UsageStats ACTIVITY_PAUSED / MOVE_TO_BACKGROUND means the app left the foreground
 * even when the last ACTIVITY_RESUMED in the stream is still that app (no launcher resume yet).
 */
object UsagePresencePolicy {

    fun isStillForeground(lastEventType: ForegroundEventType?): Boolean {
        if (lastEventType == null) return true
        return lastEventType == ForegroundEventType.RESUMED
    }
}
