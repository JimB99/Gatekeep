package com.gatekeep.app.enforcement

import com.gatekeep.domain.model.FrictionDifficulty
import com.gatekeep.domain.model.FrictionMethod

data class BlockOverlayRequest(
    val packageName: String,
    val message: String,
    val reason: String,
    val breakUntilMs: Long? = null,
    val bypassAllowed: Boolean = true,
    val frictionMethod: FrictionMethod = FrictionMethod.math,
    val difficulty: FrictionDifficulty = FrictionDifficulty.medium,
    val waitDurationSeconds: Int = 60,
    val extensionOptionMinutes: List<Int> = emptyList(),
    val showNoLimitToday: Boolean = false,
    val useExtensionButtons: Boolean = false,
    val profilePasswordHash: String? = null,
    val onProfileUnlocked: (() -> Unit)? = null,
    val isOpenGate: Boolean = false,
    val extensionsUsedToday: Int = 0,
    val maxExtensionsPerDay: Int? = null,
    val consecutiveExtensionsUsed: Int = 0,
    val maxConsecutiveExtensions: Int? = null,
)
