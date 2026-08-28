package com.gatekeep.domain

import com.gatekeep.domain.model.Pause
import com.gatekeep.domain.model.PauseType

object PauseManager {

    fun isPaused(
        pauses: List<Pause>,
        profileId: Long,
        packageName: String,
        nowEpochMs: Long,
    ): PauseCheck {
        val active = pauses.filter { it.untilEpochMs > nowEpochMs }

        val globalPause = active.firstOrNull { it.profileId == null && it.packageName == null }
        if (globalPause != null) {
            return PauseCheck.Paused(globalPause, scope = PauseScope.global)
        }

        val profilePause = active.firstOrNull { it.profileId == profileId && it.packageName == null }
        if (profilePause != null) {
            return PauseCheck.Paused(profilePause, scope = PauseScope.profile)
        }

        val appPause = active.firstOrNull { it.profileId == profileId && it.packageName == packageName }
        if (appPause != null) {
            return PauseCheck.Paused(appPause, scope = PauseScope.app)
        }

        return PauseCheck.NotPaused
    }

    fun createPause(
        type: PauseType,
        nowEpochMs: Long,
        profileId: Long? = null,
        packageName: String? = null,
        untilEpochMs: Long? = null,
    ): Pause {
        val until = untilEpochMs ?: when (type) {
            PauseType.fiveMin -> nowEpochMs + 5 * 60_000L
            PauseType.fifteenMin -> nowEpochMs + 15 * 60_000L
            PauseType.sixtyMin -> nowEpochMs + 60 * 60_000L
            PauseType.focusMode -> nowEpochMs + 25 * 60_000L
            PauseType.emergencyBypass -> nowEpochMs + 15 * 60_000L
            PauseType.untilDatetime -> error("untilDatetime requires explicit untilEpochMs")
            PauseType.noLimitToday -> error("noLimitToday requires explicit untilEpochMs")
        }
        return Pause(
            profileId = profileId,
            packageName = packageName,
            type = type,
            untilEpochMs = until,
        )
    }

    sealed class PauseCheck {
        data object NotPaused : PauseCheck()
        data class Paused(val pause: Pause, val scope: PauseScope) : PauseCheck()
    }

    enum class PauseScope {
        global,
        profile,
        app,
    }
}
