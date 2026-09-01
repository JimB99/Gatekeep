package com.gatekeep.app.enforcement

import com.gatekeep.domain.RuleEngine
import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.LimitExtensionBonus
import com.gatekeep.domain.model.Pause
import com.gatekeep.domain.model.Profile
import com.gatekeep.domain.model.ResolvedSchedulePolicy
import com.gatekeep.domain.model.RuleEvaluationContext
import com.gatekeep.domain.model.RuleResult
import com.gatekeep.domain.model.SessionState
import com.gatekeep.domain.model.UsageSnapshot

object EnforcementEvaluationUseCase {
    fun evaluate(
        packageName: String,
        profile: Profile,
        limit: AppLimit?,
        isMonitored: Boolean,
        usage: UsageSnapshot,
        sessionState: SessionState?,
        pauses: List<Pause>,
        schedulePolicy: ResolvedSchedulePolicy,
        nowEpochMs: Long,
        limitExtensionBonus: LimitExtensionBonus = LimitExtensionBonus(),
    ): RuleResult = RuleEngine.evaluate(
        RuleEvaluationContext(
            nowEpochMs = nowEpochMs,
            packageName = packageName,
            profile = profile,
            limit = limit,
            isMonitored = isMonitored,
            usage = usage,
            sessionState = sessionState,
            pauses = pauses,
            resolvedSchedulePolicy = schedulePolicy,
            enforcementConfig = profile.enforcementConfig(),
            limitExtensionBonus = limitExtensionBonus,
        ),
    )
}
