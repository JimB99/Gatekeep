package com.gatekeep.app.support

import com.gatekeep.app.BuildConfig

object EnforcementTestPackages {
  val TARGET_A: String
    get() = BuildConfig.APPLICATION_ID

  val TARGET_A_COMPONENT: String
    get() =
        "${BuildConfig.APPLICATION_ID}/com.gatekeep.app.testsupport.EnforcementTargetActivity"

  const val TARGET_B = "com.android.settings"
  const val TARGET_B_COMPONENT = "com.android.settings/.Settings"

  val TARGET_A_B_COMPONENT: String
    get() =
        "${BuildConfig.APPLICATION_ID}/com.gatekeep.app.testsupport.EnforcementTargetActivityB"

  const val UNMONITORED = "com.android.dialer"

  const val TARGET_A_LABEL = "Gatekeep Test Target A"
  const val TARGET_B_LABEL = "Settings"
}