package com.gatekeep.domain

import com.gatekeep.domain.model.ExtensionPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OverlayOptionMinutesTest {

    @Test
    fun `hides overlay minute options when daily cap is already reached`() {
        val policy = ExtensionPolicy(
            optionMinutes = listOf(1, 5, 10),
            maxExtensionsPerDay = 2,
        )

        val minutes = ExtensionPolicyEvaluator.overlayOptionMinutes(
            policy = policy,
            overridesToday = 2,
            consecutiveInSession = 0,
        )

        assertEquals(emptyList<Int>(), minutes)
    }

    @Test
    fun `hides overlay minute options when consecutive cap is already reached`() {
        val policy = ExtensionPolicy(
            optionMinutes = listOf(1, 5, 10),
            maxConsecutiveExtensions = 1,
        )

        val minutes = ExtensionPolicyEvaluator.overlayOptionMinutes(
            policy = policy,
            overridesToday = 0,
            consecutiveInSession = 1,
        )

        assertEquals(emptyList<Int>(), minutes)
    }

    @Test
    fun `keeps overlay minute options when daily and consecutive caps still have room`() {
        val policy = ExtensionPolicy(
            optionMinutes = listOf(1, 5, 10),
            maxExtensionsPerDay = 2,
            maxConsecutiveExtensions = 2,
        )

        val minutes = ExtensionPolicyEvaluator.overlayOptionMinutes(
            policy = policy,
            overridesToday = 1,
            consecutiveInSession = 1,
        )

        assertEquals(listOf(1, 5, 10), minutes)
    }

    @Test
    fun `hides overlay minute options when daily cap is used as consecutive cap`() {
        val policy = ExtensionPolicy(
            optionMinutes = listOf(5, 15),
            maxExtensionsPerDay = 2,
        )

        val minutes = ExtensionPolicyEvaluator.overlayOptionMinutes(
            policy = policy,
            overridesToday = 0,
            consecutiveInSession = 2,
        )

        assertEquals(emptyList<Int>(), minutes)
    }
}
