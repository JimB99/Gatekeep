package com.gatekeep.domain

import com.gatekeep.domain.model.ExtensionPolicy
import com.gatekeep.domain.model.LimitUsageScope
import com.gatekeep.domain.model.Profile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PolicyTimelineResolverTest {

    @Test
    fun `findNextChange returns nearest future window`() {
        val segment = com.gatekeep.domain.model.ScheduleSegment(
            id = 1L,
            profileId = 1L,
            label = "Evening",
            mode = com.gatekeep.domain.model.SchedulePolicyMode.block,
        )
        val window = com.gatekeep.domain.model.ScheduleWindow(
            id = 1L,
            profileId = 1L,
            segmentId = 1L,
            dayOfWeek = 1,
            startMinute = 18 * 60,
            endMinute = 22 * 60,
        )
        val mondayNoon = java.time.ZonedDateTime.of(2026, 1, 5, 12, 0, 0, 0, java.time.ZoneId.of("UTC"))
            .toInstant().toEpochMilli()
        val next = PolicyTimelineResolver.findNextChange(
            segments = listOf(segment),
            windows = listOf(window),
            nowEpochMs = mondayNoon,
            zoneId = java.time.ZoneId.of("UTC"),
        )
        requireNotNull(next)
        assertEquals("Evening", next.segmentLabel)
        assertEquals(6 * 60, (next.startsAtEpochMs - mondayNoon) / 60_000L)
    }
}
