package com.gatekeep.app.support

object EnforcementTestPackages {
    const val TARGET_A = "com.gatekeep.app"
    const val TARGET_A_COMPONENT = "com.gatekeep.app/.testsupport.EnforcementTargetActivity"
    const val TARGET_B = "com.android.settings"
    const val TARGET_B_COMPONENT = "com.android.settings/.Settings"
    const val TARGET_A_B_COMPONENT = "com.gatekeep.app/.testsupport.EnforcementTargetActivityB"
    const val UNMONITORED = "com.android.dialer"

    const val TARGET_A_LABEL = "Gatekeep Test Target A"
    const val TARGET_B_LABEL = "Settings"
}
