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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(R.string.week_timeline),
            style = MaterialTheme.typography.titleMedium,
        )
        days.forEach { day ->
            val dayIndex = day.value % 7
            val dayWindows = enforcementWindows
                .filter { it.dayOfWeek == dayIndex }
                .sortedBy { it.startMinute }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    day.getDisplayName(TextStyle.FULL, locale),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                if (dayWindows.isEmpty()) {
                    Text(
                        stringResource(R.string.no_schedules),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    dayWindows.forEach { window ->
                        val segment = segments.find { it.id == window.segmentId }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { window.segmentId?.let(onSegmentSelected) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    segment?.label ?: segment?.mode?.name.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    formatScheduleTimeRange(window.startMinute, window.endMinute),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
