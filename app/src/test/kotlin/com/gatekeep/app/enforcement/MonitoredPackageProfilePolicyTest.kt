package com.gatekeep.app.enforcement

import com.gatekeep.domain.model.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonitoredPackageProfilePolicyTest {

    private val youtube = "app.revanced.android.youtube"

    @Test
    fun primaryProfileId_usesLowestIdAmongMonitors() {
        val profiles = listOf(
            profile(id = 10L, name = "B"),
            profile(id = 2L, name = "A"),
            profile(id = 5L, name = "C"),
        )
        val monitors = mapOf(10L to listOf(youtube), 2L to listOf(youtube), 5L to listOf("other.app"))

        val id = MonitoredPackageProfilePolicy.primaryProfileId(
            activeProfiles = profiles,
            packageName = youtube,
        ) { profileId -> youtube in (monitors[profileId] ?: emptyList()) }

        assertEquals(2L, id)
    }

    @Test
    fun primaryProfileId_nullWhenNoMonitor() {
        val profiles = listOf(profile(id = 1L, name = "Only"))
        assertNull(
            MonitoredPackageProfilePolicy.primaryProfileId(
                activeProfiles = profiles,
                packageName = youtube,
            ) { false },
        )
    }

    @Test
    fun firstActiveProfile_canDifferFromPrimary() {
        val profiles = listOf(
            profile(id = 10L, name = "FirstInList"),
            profile(id = 2L, name = "Primary"),
        )
        val monitors = setOf(10L, 2L)

        val primary = MonitoredPackageProfilePolicy.primaryProfileId(
            activeProfiles = profiles,
            packageName = youtube,
        ) { it in monitors }

        assertEquals(2L, primary)
        assertEquals(10L, profiles.first().id)
    }

    private fun profile(id: Long, name: String) = Profile(
        id = id,
        name = name,
        isActive = true,
    )
}
