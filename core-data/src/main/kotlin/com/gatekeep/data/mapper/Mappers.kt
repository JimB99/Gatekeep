package com.gatekeep.data.mapper

import com.gatekeep.data.local.entity.AppLimitEntity
import com.gatekeep.data.local.entity.MonitoredAppEntity
import com.gatekeep.data.local.entity.PauseEntity
import com.gatekeep.data.local.entity.ProfileEntity
import com.gatekeep.data.local.entity.ScheduleWindowEntity
import com.gatekeep.data.local.entity.SessionStateEntity
import com.gatekeep.domain.model.AppCategory
import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.FrictionDifficulty
import com.gatekeep.domain.model.FrictionMethod
import com.gatekeep.domain.model.MonitoredApp
import com.gatekeep.domain.model.Pause
import com.gatekeep.domain.model.PauseType
import com.gatekeep.domain.model.Profile
import com.gatekeep.domain.model.ScheduleWindow
import com.gatekeep.domain.model.SessionState

fun ProfileEntity.toDomain() = Profile(
    id = id,
    name = name,
    isActive = isActive,
    passwordHash = passwordHash,
    lockEnabled = lockEnabled,
    sortOrder = sortOrder,
    autoScheduleEnabled = autoScheduleEnabled,
    defaultFrictionMethod = FrictionMethod.valueOf(defaultFrictionMethod),
    defaultFrictionDifficulty = FrictionDifficulty.valueOf(defaultFrictionDifficulty),
    delayOpenSeconds = delayOpenSeconds,
    gradualTighteningEnabled = gradualTighteningEnabled,
    gradualTighteningTargetDailyMs = gradualTighteningTargetDailyMs,
    gradualTighteningPercentPerWeek = gradualTighteningPercentPerWeek,
    dailyLimitMs = dailyLimitMs,
    hourlyLimitMs = hourlyLimitMs,
    weeklyLimitMs = weeklyLimitMs,
    sessionLimitMs = sessionLimitMs,
    breakDurationMs = breakDurationMs,
    waitDurationSeconds = waitDurationSeconds,
)

fun Profile.toEntity() = ProfileEntity(
    id = id,
    name = name,
    isActive = isActive,
    passwordHash = passwordHash,
    lockEnabled = lockEnabled,
    sortOrder = sortOrder,
    autoScheduleEnabled = autoScheduleEnabled,
    defaultFrictionMethod = defaultFrictionMethod.name,
    defaultFrictionDifficulty = defaultFrictionDifficulty.name,
    delayOpenSeconds = delayOpenSeconds,
    gradualTighteningEnabled = gradualTighteningEnabled,
    gradualTighteningTargetDailyMs = gradualTighteningTargetDailyMs,
    gradualTighteningPercentPerWeek = gradualTighteningPercentPerWeek,
    dailyLimitMs = dailyLimitMs,
    hourlyLimitMs = hourlyLimitMs,
    weeklyLimitMs = weeklyLimitMs,
    sessionLimitMs = sessionLimitMs,
    breakDurationMs = breakDurationMs,
    waitDurationSeconds = waitDurationSeconds,
)

fun MonitoredAppEntity.toDomain() = MonitoredApp(
    profileId = profileId,
    packageName = packageName,
    label = label,
    category = runCatching { AppCategory.valueOf(category) }.getOrDefault(AppCategory.other),
    isWhitelistedEssential = isWhitelistedEssential,
)

fun AppLimitEntity.toDomain() = AppLimit(
    profileId = profileId,
    packageName = packageName,
    dailyLimitMs = dailyLimitMs,
    weeklyLimitMs = weeklyLimitMs,
    hourlyLimitMs = hourlyLimitMs,
    sessionLimitMs = sessionLimitMs,
    breakDurationMs = breakDurationMs,
    enabled = enabled,
    frictionMethod = frictionMethod?.let { FrictionMethod.valueOf(it) },
    frictionDifficulty = frictionDifficulty?.let { FrictionDifficulty.valueOf(it) },
    extensionMsOnBypass = extensionMsOnBypass,
)

fun AppLimit.toEntity() = AppLimitEntity(
    profileId = profileId,
    packageName = packageName,
    dailyLimitMs = dailyLimitMs,
    weeklyLimitMs = weeklyLimitMs,
    hourlyLimitMs = hourlyLimitMs,
    sessionLimitMs = sessionLimitMs,
    breakDurationMs = breakDurationMs,
    enabled = enabled,
    frictionMethod = frictionMethod?.name,
    frictionDifficulty = frictionDifficulty?.name,
    extensionMsOnBypass = extensionMsOnBypass,
)

fun ScheduleWindowEntity.toDomain() = ScheduleWindow(
    id = id,
    profileId = profileId,
    packageName = packageName,
    dayOfWeek = dayOfWeek,
    startMinute = startMinute,
    endMinute = endMinute,
    isProfileAutoSwitch = isProfileAutoSwitch,
)

fun PauseEntity.toDomain() = Pause(
    id = id,
    profileId = profileId,
    packageName = packageName,
    type = PauseType.valueOf(type),
    untilEpochMs = untilEpochMs,
)

fun SessionStateEntity.toDomain() = SessionState(
    packageName = packageName,
    sessionStartEpochMs = sessionStartEpochMs,
    breakUntilEpochMs = breakUntilEpochMs,
)

fun SessionState.toEntity(profileId: Long) = SessionStateEntity(
    packageName = packageName,
    profileId = profileId,
    sessionStartEpochMs = sessionStartEpochMs,
    breakUntilEpochMs = breakUntilEpochMs,
)
