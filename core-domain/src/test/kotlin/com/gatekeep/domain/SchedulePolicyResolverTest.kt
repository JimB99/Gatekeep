package com.gatekeep.domain

import com.gatekeep.domain.model.PolicySource
import com.gatekeep.domain.model.Profile
import com.gatekeep.domain.model.ResolvedSchedulePolicy
import com.gatekeep.domain.model.SchedulePolicyMode
import com.gatekeep.domain.model.ScheduleSegment
import com.gatekeep.domain.model.ScheduleWindow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SchedulePolicyResolverTest {

    private val profile = Profile(id = 1, name = "Test", isActive = true)

    @Test
    fun `no matching segment uses noScheduleMatchMode default`() {
        val policy = SchedulePolicyResolver.resolveForProfile(
            profile = profile,
            segments = emptyList(),
            windows = emptyList(),
            packageName = "com.test",
            nowEpochMs = mondayAt(10, ZoneId.of("UTC")),
        )
        assertEquals(SchedulePolicyMode.default, policy.mode)
        assertEquals(PolicySource.noScheduleMatch, policy.source)
        assertTrue(SchedulePolicyResolver.isAppAvailable(policy))
    }

    @Test
    fun `no matching segment uses block fallback`() {
        val policy = SchedulePolicyResolver.resolveForProfile(
            profile = profile.copy(noScheduleMatchMode = SchedulePolicyMode.block),
            segments = emptyList(),
            windows = emptyList(),
            packageName = "com.test",
            nowEpochMs = mondayAt(10, ZoneId.of("UTC")),
        )
        assertEquals(SchedulePolicyMode.block, policy.mode)
        assertFalse(SchedulePolicyResolver.isAppAvailable(policy))
    }

    @Test
    fun `matching allow segment returns allow`() {
        val segment = ScheduleSegment(id = 1, profileId = 1, mode = SchedulePolicyMode.allow)
        val windows = listOf(
            ScheduleWindow(profileId = 1, segmentId = 1, dayOfWeek = 1, startMinute = 9 * 60, endMinute = 17 * 60),
        )
        val policy = SchedulePolicyResolver.resolveForProfile(
            profile = profile,
            segments = listOf(segment),
            windows = windows,
            packageName = "com.test",
            nowEpochMs = mondayAt(10, ZoneId.of("UTC")),
        )
        assertEquals(SchedulePolicyMode.allow, policy.mode)
        assertEquals(1L, policy.activeSegmentId)
        assertTrue(SchedulePolicyResolver.isAppAvailable(policy))
    }

    @Test
    fun `inactive segment is ignored`() {
        val segment = ScheduleSegment(id = 1, profileId = 1, isActive = false, mode = SchedulePolicyMode.block)
        val windows = listOf(
            ScheduleWindow(profileId = 1, segmentId = 1, dayOfWeek = 1, startMinute = 9 * 60, endMinute = 17 * 60),
        )
        val policy = SchedulePolicyResolver.resolveForProfile(
            profile = profile,
            segments = listOf(segment),
            windows = windows,
            packageName = "com.test",
            nowEpochMs = mondayAt(10, ZoneId.of("UTC")),
        )
        assertEquals(SchedulePolicyMode.default, policy.mode)
        assertEquals(PolicySource.noScheduleMatch, policy.source)
    }

    @Test
    fun `block wins over allow on overlap`() {
        val allowSeg = ScheduleSegment(id = 1, profileId = 1, mode = SchedulePolicyMode.allow)
        val blockSeg = ScheduleSegment(id = 2, profileId = 1, mode = SchedulePolicyMode.block)
        val windows = listOf(
            ScheduleWindow(profileId = 1, segmentId = 1, dayOfWeek = 1, startMinute = 9 * 60, endMinute = 17 * 60),
            ScheduleWindow(profileId = 1, segmentId = 2, dayOfWeek = 1, startMinute = 9 * 60, endMinute = 17 * 60),
        )
        val policy = SchedulePolicyResolver.resolveForProfile(
            profile = profile,
            segments = listOf(allowSeg, blockSeg),
            windows = windows,
            packageName = "com.test",
            nowEpochMs = mondayAt(10, ZoneId.of("UTC")),
        )
        assertEquals(SchedulePolicyMode.block, policy.mode)
    }

    @Test
    fun `customize segment does not inherit unset limits from profile`() {
        val segment = ScheduleSegment(
            id = 1,
            profileId = 1,
            mode = SchedulePolicyMode.customize,
            overrides = com.gatekeep.domain.model.SchedulePolicyOverrides(dailyLimitMs = 30 * 60_000L),
        )
        val windows = listOf(
            ScheduleWindow(profileId = 1, segmentId = 1, dayOfWeek = 1, startMinute = 9 * 60, endMinute = 17 * 60),
        )
        val policy = SchedulePolicyResolver.resolveForProfile(
            profile = profile.copy(
                weeklyLimitMs = 7 * 60 * 60_000L,
                dailyLimitMs = 60 * 60_000L,
                hourlyLimitMs = 30 * 60_000L,
            ),
            segments = listOf(segment),
            windows = windows,
            packageName = "com.test",
            nowEpochMs = mondayAt(10, ZoneId.of("UTC")),
        )
        assertEquals(30 * 60_000L, policy.limits?.dailyLimitMs)
        assertNull(policy.limits?.weeklyLimitMs)
        assertNull(policy.limits?.hourlyLimitMs)
    }

    @Test
    fun `customize segment applies override limits`() {
        val segment = ScheduleSegment(
            id = 1,
            profileId = 1,
            mode = SchedulePolicyMode.customize,
            overrides = com.gatekeep.domain.model.SchedulePolicyOverrides(dailyLimitMs = 30 * 60_000L),
        )
        val windows = listOf(
            ScheduleWindow(profileId = 1, segmentId = 1, dayOfWeek = 1, startMinute = 9 * 60, endMinute = 17 * 60),
        )
        val policy = SchedulePolicyResolver.resolveForProfile(
            profile = profile.copy(dailyLimitMs = 60 * 60_000L),
            segments = listOf(segment),
            windows = windows,
            packageName = "com.test",
            nowEpochMs = mondayAt(10, ZoneId.of("UTC")),
        )
        assertEquals(SchedulePolicyMode.customize, policy.mode)
        assertEquals(30 * 60_000L, policy.limits?.dailyLimitMs)
    }

    @Test
    fun `overnight window matches after midnight`() {
        val segment = ScheduleSegment(id = 1, profileId = 1, mode = SchedulePolicyMode.block)
        val windows = listOf(
            ScheduleWindow(profileId = 1, segmentId = 1, dayOfWeek = 1, startMinute = 22 * 60, endMinute = 7 * 60),
        )
        val policy = SchedulePolicyResolver.resolveForProfile(
            profile = profile,
            segments = listOf(segment),
            windows = windows,
            packageName = "com.test",
            nowEpochMs = mondayAt(6, ZoneId.of("UTC")) + 86_400_000L,
            zoneId = ZoneId.of("UTC"),
        )
        assertEquals(SchedulePolicyMode.block, policy.mode)
    }

    private fun mondayAt(hour: Int, zoneId: ZoneId): Long =
        ZonedDateTime.of(2025, 1, 6, hour, 0, 0, 0, zoneId).toInstant().toEpochMilli()

    @Test
    fun `equal mode schedules resolve by sortOrder then id`() {
        val firstSeg = ScheduleSegment(id = 10, profileId = 1, mode = SchedulePolicyMode.allow, sortOrder = 2)
        val secondSeg = ScheduleSegment(id = 5, profileId = 1, mode = SchedulePolicyMode.allow, sortOrder = 1)
        val windows = listOf(
            ScheduleWindow(profileId = 1, segmentId = 10, dayOfWeek = 1, startMinute = 9 * 60, endMinute = 17 * 60),
            ScheduleWindow(profileId = 1, segmentId = 5, dayOfWeek = 1, startMinute = 9 * 60, endMinute = 17 * 60),
        )
        val policy = SchedulePolicyResolver.resolveForProfile(
            profile = profile,
            segments = listOf(firstSeg, secondSeg),
            windows = windows,
            packageName = "com.test",
            nowEpochMs = mondayAt(10, ZoneId.of("UTC")),
        )
        assertEquals(5L, policy.activeSegmentId)
    }
}
