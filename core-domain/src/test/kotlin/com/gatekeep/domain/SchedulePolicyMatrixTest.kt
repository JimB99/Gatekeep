package com.gatekeep.domain

import com.gatekeep.domain.model.OnLimitAction
import com.gatekeep.domain.model.OnOpenAction
import com.gatekeep.domain.model.OnSessionLimitAction
import com.gatekeep.domain.model.PolicySource
import com.gatekeep.domain.model.Profile
import com.gatekeep.domain.model.SchedulePolicyMode
import com.gatekeep.domain.model.SchedulePolicyOverrides
import com.gatekeep.domain.model.ScheduleSegment
import com.gatekeep.domain.model.ScheduleWindow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.ZoneId

class SchedulePolicyMatrixTest {

    private val profile = Profile(id = 1, name = "Test", isActive = true)

    @ParameterizedTest
    @EnumSource(SchedulePolicyMode::class)
    fun noMatchMode_resolvesExpectedAvailability(mode: SchedulePolicyMode) {
        val policy = SchedulePolicyResolver.resolveForProfile(
            profile = profile.copy(noScheduleMatchMode = mode),
            segments = emptyList(),
            windows = emptyList(),
            packageName = "com.test",
            nowEpochMs = mondayAt(10, ZoneId.of("UTC")),
        )
        assertEquals(mode, policy.mode)
        assertEquals(PolicySource.noScheduleMatch, policy.source)
        when (mode) {
            SchedulePolicyMode.allow -> assertTrue(SchedulePolicyResolver.isAppAvailable(policy))
            SchedulePolicyMode.block -> assertFalse(SchedulePolicyResolver.isAppAvailable(policy))
            SchedulePolicyMode.default, SchedulePolicyMode.customize ->
                assertTrue(SchedulePolicyResolver.isAppAvailable(policy))
        }
    }

    @ParameterizedTest
    @EnumSource(value = SchedulePolicyMode::class, names = ["customize"])
    fun customizeSegment_overridesOpenAction(mode: SchedulePolicyMode) {
        val segment = ScheduleSegment(
            id = 1,
            profileId = 1,
            mode = SchedulePolicyMode.customize,
            overrides = SchedulePolicyOverrides(onOpenAction = OnOpenAction.deterrentMath),
        )
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
        assertEquals(OnOpenAction.deterrentMath, policy.enforcementConfig?.onOpenAction)
    }

    @ParameterizedTest
    @EnumSource(value = OnLimitAction::class, names = ["hardBlock", "notifyOnly", "limitWithExtensions"])
    fun noMatchCustomize_overridesLimitAction(limitAction: OnLimitAction) {
        val profileWithOverrides = profile.copy(
            noScheduleMatchMode = SchedulePolicyMode.customize,
            noScheduleMatchOverrides = SchedulePolicyOverrides(onLimitAction = limitAction),
        )
        val policy = SchedulePolicyResolver.resolveForProfile(
            profile = profileWithOverrides,
            segments = emptyList(),
            windows = emptyList(),
            packageName = "com.test",
            nowEpochMs = mondayAt(10, ZoneId.of("UTC")),
        )
        assertEquals(limitAction, policy.enforcementConfig?.onLimitAction)
    }

    @ParameterizedTest
    @EnumSource(value = OnSessionLimitAction::class, names = ["hardBlock", "notifyOnly", "mandatoryBreak"])
    fun noMatchCustomize_overridesSessionAction(sessionAction: OnSessionLimitAction) {
        val profileWithOverrides = profile.copy(
            noScheduleMatchMode = SchedulePolicyMode.customize,
            noScheduleMatchOverrides = SchedulePolicyOverrides(onSessionLimitAction = sessionAction),
        )
        val policy = SchedulePolicyResolver.resolveForProfile(
            profile = profileWithOverrides,
            segments = emptyList(),
            windows = emptyList(),
            packageName = "com.test",
            nowEpochMs = mondayAt(10, ZoneId.of("UTC")),
        )
        assertEquals(sessionAction, policy.enforcementConfig?.onSessionLimitAction)
    }

    private fun mondayAt(hour: Int, zoneId: ZoneId): Long {
        val zdt = java.time.ZonedDateTime.of(2024, 1, 1, hour, 0, 0, 0, zoneId)
        return zdt.toInstant().toEpochMilli()
    }
}
