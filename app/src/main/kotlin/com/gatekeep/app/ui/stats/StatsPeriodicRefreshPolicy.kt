package com.gatekeep.app.ui.stats

object StatsPeriodicRefreshPolicy {
    /** Live usage tick only while viewing the current stats period (not a historical anchor). */
    fun shouldRefreshPeriodically(viewingCurrentPeriod: Boolean): Boolean = viewingCurrentPeriod
}
