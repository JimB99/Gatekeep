package com.gatekeep.app.enforcement

import com.gatekeep.domain.model.BlockPresentationReason
import com.gatekeep.domain.model.ExtensionSurfaceMode
import com.gatekeep.domain.model.FrictionMethod
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayFrictionGrantPolicyTest {

    @Test
    fun sessionWait_grantsRefillEvenWhenOverlaySurfaceIsNone() {
        val request = request(
            reason = BlockPresentationReason.sessionLimit,
            extensionOptionMinutes = emptyList(),
        )
        assertEquals(
            OverlayFrictionGrantPolicy.Grant.SessionRefill,
            OverlayFrictionGrantPolicy.grant(request),
        )
    }

    @Test
    fun sessionMath_grantsRefillWhenFiveIsNotAnOption() {
        val request = request(
            reason = BlockPresentationReason.sessionLimit,
            frictionMethod = FrictionMethod.math,
            extensionOptionMinutes = listOf(1, 10),
        )
        assertEquals(
            OverlayFrictionGrantPolicy.Grant.SessionRefill,
            OverlayFrictionGrantPolicy.grant(request),
        )
        assertEquals(false, request.extensionOptionMinutes.contains(5))
    }

    @Test
    fun openGate_doesNotGrantSessionRefill() {
        val request = request(
            reason = BlockPresentationReason.openGate,
            isOpenGate = true,
        )
        assertEquals(
            OverlayFrictionGrantPolicy.Grant.OpenGate,
            OverlayFrictionGrantPolicy.grant(request),
        )
    }

    @Test
    fun periodWait_grantsContinueNotExtension() {
        val request = request(reason = BlockPresentationReason.dailyLimit)
        assertEquals(
            OverlayFrictionGrantPolicy.Grant.PeriodContinue,
            OverlayFrictionGrantPolicy.grant(request),
        )
    }

    @Test
    fun periodMath_grantsContinueWhenFiveIsNotAnOption() {
        val request = request(
            reason = BlockPresentationReason.hourlyLimit,
            frictionMethod = FrictionMethod.math,
            extensionOptionMinutes = listOf(1, 10),
        )
        assertEquals(
            OverlayFrictionGrantPolicy.Grant.PeriodContinue,
            OverlayFrictionGrantPolicy.grant(request),
        )
        assertEquals(false, request.extensionOptionMinutes.contains(5))
    }

    @Test
    fun unknownReason_completesFrictionWithNoTimeGrant() {
        val request = request(reason = BlockPresentationReason.unknown)
        assertEquals(
            OverlayFrictionGrantPolicy.Grant.None,
            OverlayFrictionGrantPolicy.grant(request),
        )
    }

    @Test
    fun sessionRefill_doesNotDependOnExtensionSurfaceMode() {
        ExtensionSurfaceMode.entries.forEach { _ ->
            assertEquals(
                OverlayFrictionGrantPolicy.Grant.SessionRefill,
                OverlayFrictionGrantPolicy.grant(
                    request(
                        reason = BlockPresentationReason.sessionLimit,
                        extensionOptionMinutes = emptyList(),
                    ),
                ),
            )
        }
    }

    private fun request(
        reason: BlockPresentationReason,
        frictionMethod: FrictionMethod = FrictionMethod.waitOneMin,
        extensionOptionMinutes: List<Int> = emptyList(),
        isOpenGate: Boolean = false,
    ) = BlockOverlayRequest(
        packageName = "com.example.app",
        message = "Session limit reached",
        reason = reason,
        bypassAllowed = true,
        frictionMethod = frictionMethod,
        extensionOptionMinutes = extensionOptionMinutes,
        useExtensionButtons = false,
        isOpenGate = isOpenGate,
    )
}
