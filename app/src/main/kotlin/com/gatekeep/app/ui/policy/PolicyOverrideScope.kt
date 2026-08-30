package com.gatekeep.app.ui.policy

sealed class PolicyOverrideScope {
    abstract val profileId: Long

    data class NoScheduleMatch(override val profileId: Long) : PolicyOverrideScope()

    data class Segment(override val profileId: Long, val segmentId: Long) : PolicyOverrideScope()
}
