package com.gatekeep.data.mapper

import com.gatekeep.domain.model.ExtensionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtensionPolicyCodecTest {

    @Test
    fun `custom minutes survives round trip when chip off`() {
        val policy = ExtensionPolicy(
            optionMinutes = listOf(1, 5, 10),
            customMinutes = 25,
        )
        val decoded = decodeExtensionPolicy(encodeExtensionPolicy(policy))
        assertEquals(25, decoded.customMinutes)
        assertNull(decoded.optionMinutes.firstOrNull { it == 25 })
    }
}
