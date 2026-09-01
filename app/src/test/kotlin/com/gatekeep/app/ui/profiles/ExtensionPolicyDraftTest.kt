package com.gatekeep.app.ui.profiles

import com.gatekeep.domain.model.ExtensionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionPolicyDraftTest {

    @Test
    fun `round trips showNoLimitToday false`() {
        val policy = ExtensionPolicy(showNoLimitToday = false)
        val draft = ExtensionPolicyDraft.fromPolicy(policy)
        assertFalse(draft.showNoLimitToday)
        assertFalse(draft.toPolicy().showNoLimitToday)
    }

    @Test
    fun `round trips showNoLimitToday true`() {
        val policy = ExtensionPolicy(showNoLimitToday = true)
        val draft = ExtensionPolicyDraft.fromPolicy(policy)
        assertTrue(draft.showNoLimitToday)
        assertTrue(draft.toPolicy().showNoLimitToday)
    }

    @Test
    fun `caps consecutive extensions to daily maximum`() {
        val draft = ExtensionPolicyDraft(
            selectedPresets = setOf(5),
            customEnabled = false,
            customMinutesText = "",
            maxPerDay = 3,
            maxConsecutive = 5,
            showNoLimitToday = true,
        )
        assertEquals(3, draft.toPolicy().maxConsecutiveExtensions)
    }

    @Test
    fun `toPolicy caps consecutive when above daily maximum`() {
        val draft = ExtensionPolicyDraft(
            selectedPresets = setOf(5),
            customEnabled = false,
            customMinutesText = "",
            maxPerDay = 2,
            maxConsecutive = 5,
            showNoLimitToday = false,
        )
        val policy = draft.toPolicy()
        assertEquals(2, policy.maxExtensionsPerDay)
        assertEquals(2, policy.maxConsecutiveExtensions)
    }
}
