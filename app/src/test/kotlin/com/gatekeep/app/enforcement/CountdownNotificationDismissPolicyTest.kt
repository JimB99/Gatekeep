package com.gatekeep.app.enforcement

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CountdownNotificationDismissPolicyTest {

    @Test
    fun refreshWhenEnabledAndNotDismissed() {
        assertTrue(
            CountdownNotificationDismissPolicy.shouldRefreshCountdown(
                showCountdownNotification = true,
                dismissedByUser = false,
            ),
        )
    }

    @Test
    fun noRefreshWhenUserDismissedInSession() {
        assertFalse(
            CountdownNotificationDismissPolicy.shouldRefreshCountdown(
                showCountdownNotification = true,
                dismissedByUser = true,
            ),
        )
    }

    @Test
    fun noRefreshWhenTimerDisabled() {
        assertFalse(
            CountdownNotificationDismissPolicy.shouldRefreshCountdown(
                showCountdownNotification = false,
                dismissedByUser = false,
            ),
        )
    }
}
