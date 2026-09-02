package com.gatekeep.domain

import com.gatekeep.domain.model.ExtensionPolicy
import com.gatekeep.domain.model.ExtensionSurfaceMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtensionRequestEvaluatorTest {

    private val policy = ExtensionPolicy(
        optionMinutes = listOf(5, 15),
        maxExtensionsPerDay = 3,
        maxConsecutiveExtensions = 2,
        surfaceMode = ExtensionSurfaceMode.both,
    )

    @Test
    fun `overlay denies minutes not in policy options`() {
        val denied = ExtensionRequestEvaluator.evaluate(
            policy = policy,
            source = ExtensionGrantSource.overlay,
            requestedMinutes = 60,
            overridesToday = 0,
            consecutiveInSession = 0,
        )
        assertTrue(denied is ExtensionPolicyEvaluator.ExtensionDecision.Denied)
    }

    @Test
    fun `in app allows current usage minutes not in overlay options`() {
        val defaultOptions = policy.copy(optionMinutes = listOf(1, 5, 10))
        val allowed15 = ExtensionRequestEvaluator.evaluate(
            policy = defaultOptions,
            source = ExtensionGrantSource.inApp,
            requestedMinutes = 15,
            overridesToday = 0,
            consecutiveInSession = 0,
        )
        val allowed60 = ExtensionRequestEvaluator.evaluate(
            policy = defaultOptions,
            source = ExtensionGrantSource.inApp,
            requestedMinutes = 60,
            overridesToday = 0,
            consecutiveInSession = 0,
        )
        assertEquals(ExtensionPolicyEvaluator.ExtensionDecision.Allowed(15), allowed15)
        assertEquals(ExtensionPolicyEvaluator.ExtensionDecision.Allowed(60), allowed60)
    }

    @Test
    fun `in app ignores overlay quotas`() {
        val strict = policy.copy(maxExtensionsPerDay = 1, maxConsecutiveExtensions = 1)
        val allowed = ExtensionRequestEvaluator.evaluate(
            policy = strict,
            source = ExtensionGrantSource.inApp,
            requestedMinutes = 15,
            overridesToday = 5,
            consecutiveInSession = 5,
        )
        assertEquals(ExtensionPolicyEvaluator.ExtensionDecision.Allowed(15), allowed)
    }

    @Test
    fun `rejects zero minutes`() {
        val denied = ExtensionRequestEvaluator.evaluate(
            policy = policy,
            source = ExtensionGrantSource.overlay,
            requestedMinutes = 0,
            overridesToday = 0,
            consecutiveInSession = 0,
        )
        assertTrue(denied is ExtensionPolicyEvaluator.ExtensionDecision.Denied)
    }

    @Test
    fun `rejects overlay when surface disabled`() {
        val overlayOnlyOff = policy.copy(surfaceMode = ExtensionSurfaceMode.inApp)
        val denied = ExtensionRequestEvaluator.evaluate(
            policy = overlayOnlyOff,
            source = ExtensionGrantSource.overlay,
            requestedMinutes = 5,
            overridesToday = 0,
            consecutiveInSession = 0,
        )
        assertTrue(denied is ExtensionPolicyEvaluator.ExtensionDecision.Denied)
    }

    @Test
    fun `rejects in app when surface disabled`() {
        val inAppOff = policy.copy(surfaceMode = ExtensionSurfaceMode.overlay)
        val denied = ExtensionRequestEvaluator.evaluate(
            policy = inAppOff,
            source = ExtensionGrantSource.inApp,
            requestedMinutes = 5,
            overridesToday = 0,
            consecutiveInSession = 0,
        )
        assertTrue(denied is ExtensionPolicyEvaluator.ExtensionDecision.Denied)
    }

    @Test
    fun `allows valid in app request`() {
        val allowed = ExtensionRequestEvaluator.evaluate(
            policy = policy,
            source = ExtensionGrantSource.inApp,
            requestedMinutes = 5,
            overridesToday = 0,
            consecutiveInSession = 0,
        )
        assertEquals(
            ExtensionPolicyEvaluator.ExtensionDecision.Allowed(5),
            allowed,
        )
    }
}
