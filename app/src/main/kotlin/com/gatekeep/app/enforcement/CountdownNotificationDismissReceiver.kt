package com.gatekeep.app.enforcement

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Records that the user swiped away the session-timer HUD notification. */
class CountdownNotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        CountdownNotificationState.markDismissedByUser()
    }
}

internal object CountdownNotificationState {
    @Volatile
    var dismissedByUser: Boolean = false

    fun markDismissedByUser() {
        dismissedByUser = true
    }

    fun reset() {
        dismissedByUser = false
    }
}
