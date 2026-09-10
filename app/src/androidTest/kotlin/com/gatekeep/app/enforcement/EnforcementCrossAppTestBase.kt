package com.gatekeep.app.enforcement

import androidx.test.filters.LargeTest
import com.gatekeep.app.support.GatekeepInstrumentedTestBase
import com.gatekeep.app.support.GatekeepTestFixtures
import com.gatekeep.domain.model.OnLimitAction
import org.junit.Before

@LargeTest
abstract class EnforcementCrossAppTestBase : GatekeepInstrumentedTestBase() {

    @Before
    override fun baseSetUp() {
        super.baseSetUp()
        prepareEnforcementEnvironment()
    }

    protected fun seedHardBlockProfile(
        packageName: String = com.gatekeep.app.support.EnforcementTestPackages.TARGET_A,
    ) = seedBlockedProfile(
        packageName = packageName,
        config = GatekeepTestFixtures.ProfileSeedConfig(onLimitAction = OnLimitAction.hardBlock),
    )
}
