package com.gatekeep.data.mapper

import com.gatekeep.domain.model.ExtensionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ExtensionPolicyCodecTest {

    @Test
    fun `custom minutes survives round trip when chip off`() {
        val policy = ExtensionPolicy(
            optionMinutes = listOf(1, 5, 10),
            customMinutes = 25,
            customEnabled = false,
        )
        val decoded = decodeExtensionPolicy(encodeExtensionPolicy(policy))
        assertEquals(25, decoded.customMinutes)
        assertFalse(decoded.customEnabled)
        assertNull(decoded.optionMinutes.firstOrNull { it == 25 })
    }

    @Test
    fun `migrates legacy json without customEnabled flag`() {
        val legacy = """{"optionMinutes":[1,5,10,25],"customMinutes":25}"""
        val decoded = decodeExtensionPolicy(legacy)
        assertEquals(25, decoded.customMinutes)
        assertEquals(true, decoded.customEnabled)
    }
}
