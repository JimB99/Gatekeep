package com.gatekeep.app.ui.schedule

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gatekeep.app.R
import com.gatekeep.domain.ScheduleWindowMatcher

internal fun formatMinute(minute: Int): String =
    "%02d:%02d".format(minute / 60, minute % 60)

@Composable
fun formatScheduleTimeRange(startMinute: Int, endMinute: Int): String {
    val range = "${formatMinute(startMinute)} – ${formatMinute(endMinute)}"
    return if (ScheduleWindowMatcher.isOvernight(startMinute, endMinute)) {
        "$range ${stringResource(R.string.schedule_next_day_suffix)}"
    } else {
        range
    }
}
