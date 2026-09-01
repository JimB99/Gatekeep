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
    fun `in app uses same validation as overlay`() {
        val denied = ExtensionRequestEvaluator.evaluate(
            policy = policy,
            source = ExtensionGrantSource.inApp,
            requestedMinutes = 99,
            overridesToday = 0,
            consecutiveInSession = 0,
        )
        assertTrue(denied is ExtensionPolicyEvaluator.ExtensionDecision.Denied)
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
