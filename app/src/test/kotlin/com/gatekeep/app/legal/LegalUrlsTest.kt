package com.gatekeep.app.legal

import org.junit.Assert.assertTrue
import org.junit.Test

class LegalUrlsTest {
    @Test
    fun urlsPointAtBjbrosLegal() {
        assertTrue(LegalUrls.PRIVACY.contains("bjbros-legal.web.app"))
        assertTrue(LegalUrls.PRIVACY.contains("local-android"))
    }
}
