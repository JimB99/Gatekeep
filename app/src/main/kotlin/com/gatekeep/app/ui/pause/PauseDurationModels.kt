package com.gatekeep.app.ui.pause

import com.gatekeep.domain.model.Pause
import com.gatekeep.domain.model.PauseType

sealed interface DurationChoice {
    data class PresetMinutes(val minutes: Int) : DurationChoice
    data class CustomMinutes(val minutes: Int) : DurationChoice
    data object Today : DurationChoice
    data class UntilDateTime(val untilEpochMs: Long) : DurationChoice
}

fun DurationChoice.sameKindAs(other: DurationChoice?): Boolean {
    if (other == null) return false
    return when (this) {
        is DurationChoice.PresetMinutes -> other is DurationChoice.PresetMinutes
        is DurationChoice.CustomMinutes -> other is DurationChoice.CustomMinutes
        DurationChoice.Today -> other is DurationChoice.Today
        is DurationChoice.UntilDateTime -> other is DurationChoice.UntilDateTime
    }
}

fun resolveActiveDurationChoice(pause: Pause?, nowEpochMs: Long): DurationChoice? {
    if (pause == null || pause.untilEpochMs <= nowEpochMs) return null
    return when (pause.type) {
        PauseType.fiveMin -> DurationChoice.PresetMinutes(5)
        PauseType.fifteenMin -> DurationChoice.PresetMinutes(15)
        PauseType.sixtyMin -> DurationChoice.PresetMinutes(60)
        PauseType.noLimitToday -> DurationChoice.Today
        PauseType.untilDatetime -> {
            val remainingMs = pause.untilEpochMs - nowEpochMs
            if (remainingMs > 24 * 60 * 60_000L) {
                DurationChoice.UntilDateTime(pause.untilEpochMs)
            } else {
                val minutes = (remainingMs / 60_000L).toInt().coerceIn(1, 999)
                when (minutes) {
                    5 -> DurationChoice.PresetMinutes(5)
                    15 -> DurationChoice.PresetMinutes(15)
                    60 -> DurationChoice.PresetMinutes(60)
                    else -> DurationChoice.CustomMinutes(minutes)
                }
            }
        }
        else -> null
    }
}

fun resolveScopePause(
    pauses: List<Pause>,
    profileIds: List<Long>?,
    nowEpochMs: Long,
    focusBlock: Boolean,
): Pause? {
    val active = pauses.filter {
        it.untilEpochMs > nowEpochMs &&
            it.packageName == null &&
            (it.type == PauseType.focusBlock) == focusBlock
    }
    return when {
        profileIds == null -> active.maxByOrNull { it.untilEpochMs }
        profileIds.size == 1 -> active.filter { it.profileId == profileIds.first() }.maxByOrNull { it.untilEpochMs }
        else -> active.filter { it.profileId in profileIds }.maxByOrNull { it.untilEpochMs }
    }
}
