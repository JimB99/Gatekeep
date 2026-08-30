package com.gatekeep.domain

import com.gatekeep.domain.model.Pause
import com.gatekeep.domain.model.PauseType

object FocusBlockManager {

    fun isBlocked(
        pauses: List<Pause>,
        profileId: Long,
        nowEpochMs: Long,
    ): BlockCheck {
        val active = pauses.filter {
            it.untilEpochMs > nowEpochMs &&
                it.type == PauseType.focusBlock &&
                it.packageName == null
        }

        val globalBlock = active.firstOrNull { it.profileId == null }
        if (globalBlock != null) {
            return BlockCheck.Blocked(globalBlock, scope = BlockScope.global)
        }

        val profileBlock = active.firstOrNull { it.profileId == profileId }
        if (profileBlock != null) {
            return BlockCheck.Blocked(profileBlock, scope = BlockScope.profile)
        }

        return BlockCheck.NotBlocked
    }

    sealed class BlockCheck {
        data object NotBlocked : BlockCheck()
        data class Blocked(val pause: Pause, val scope: BlockScope) : BlockCheck()
    }

    enum class BlockScope {
        global,
        profile,
    }
}
