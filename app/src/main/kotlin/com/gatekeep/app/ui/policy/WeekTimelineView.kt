@file:OptIn(ExperimentalMaterial3Api::class)

package com.gatekeep.app.ui.policy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gatekeep.app.R
import com.gatekeep.app.ui.schedule.formatScheduleTimeRange
import com.gatekeep.domain.model.ScheduleSegment
import com.gatekeep.domain.model.ScheduleWindow
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun WeekTimelineView(
    segments: List<ScheduleSegment>,
    windows: List<ScheduleWindow>,
    locale: Locale,
    modifier: Modifier = Modifier,
    onSegmentSelected: (Long) -> Unit = {},
) {
    val firstDay = java.time.temporal.WeekFields.of(locale).firstDayOfWeek
    val days = (0 until 7).map { offset ->
        DayOfWeek.of((firstDay.value - 1 + offset) % 7 + 1)
    }
    val enforcementWindows = windows.filter { !it.isProfileAutoSwitch }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.week_timeline),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.forEach { day ->
                val dayIndex = day.value % 7
                val dayWindows = enforcementWindows.filter { it.dayOfWeek == dayIndex }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        day.getDisplayName(TextStyle.SHORT, locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    dayWindows.forEach { window ->
                        val segment = segments.find { it.id == window.segmentId }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { window.segmentId?.let(onSegmentSelected) },
                        ) {
                            Column(Modifier.padding(6.dp)) {
                                Text(
                                    segment?.label ?: segment?.mode?.name.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2,
                                )
                                Text(
                                    formatScheduleTimeRange(window.startMinute, window.endMinute),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
