# Gatekeep test scenarios

Regression catalog for automated and manual testing. Each scenario ID maps to one or more tests.

## Running tests

```bash
cd Gatekeep
export JAVA_HOME="../.tools/jdk-17.0.14+7"
export ANDROID_HOME="../.tools/android-sdk"

# JVM unit tests (fast)
./gradlew :core-domain:test :app:testDebugUnitTest

# Emulator instrumented tests (start AVD first)
./gradlew :app:connectedDebugAndroidTest

# Single class
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.gatekeep.app.enforcement.OverlayStabilityTest

# Skip slow cross-app tests (@LargeTest)
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.notAnnotation=androidx.test.filters.LargeTest
```

**Prerequisites:** Android emulator running (`Medium_Phone_API_36.0` or similar), `local.properties` with `sdk.dir`, debug keystore at `keystore/debug.keystore`.

Gradle uses JDK 21 via `org.gradle.java.home` in `gradle.properties` (Studio JBR).

---

## PIN — App lock

| ID | Scenario | Automated test |
|----|----------|----------------|
| PIN-01 | Cold start with lock enabled | `MainActivityAppLockTest.pin01_coldStart_showsLockScreen` |
| PIN-02 | Correct PIN unlocks to dashboard | `AppLockScreenTest.pin02_*`, `MainActivityAppLockTest.pin02_*` |
| PIN-03 | Wrong PIN stays locked | `AppLockScreenTest.pin03_wrongPin_staysLocked` |
| PIN-04 | Background (ON_STOP) requires PIN again | `MainActivityAppLockTest.pin04_backgroundingRequiresPinAgain` |
| PIN-05 | Screen lock requires PIN on wake | Manual (`ManualScenariosTest.pin05_screenLockRequiresPinOnWake`) |
| PIN-06 | Force-stop requires PIN on relaunch | Manual (`ManualScenariosTest.pin06_forceStopRequiresPinOnRelaunch`) |
| PIN-07 | Rotation does not re-lock | `MainActivityAppLockTest.pin07_rotation_staysUnlocked` |
| PIN-08 | Lock disabled skips PIN | `MainActivityAppLockTest.pin08_lockDisabled_skipsLockScreen` |
| PIN-09 | No PIN hash skips lock | `MainActivityAppLockTest.pin09_noPin_skipsLockScreen` |
| PIN-10 | Rapid background/resume always locks | `MainActivityAppLockTest.pin10_rapidBackgroundResume_requiresPin` |

---

## OB — Onboarding

| ID | Scenario | Automated test |
|----|----------|----------------|
| OB-01 | Fresh install starts at onboarding | `OnboardingTest.ob01_freshInstall_startsAtOnboarding` |
| OB-02 | Get started disabled until core permissions | `OnboardingTest.ob02_getStarted_disabledUntilPermissions` |
| OB-03 | Complete onboarding reaches dashboard | `OnboardingTest.ob03_completeOnboarding_reachesDashboard` |

---

## P — Permissions banner

| ID | Scenario | Automated test |
|----|----------|----------------|
| P-01 | All granted hides banner | `PermissionBannerTest.p01_allGranted_hidesBanner` |
| P-02 | Missing usage shows banner | `PermissionBannerTest.p02_missingUsage_showsBanner` |
| P-03 | Missing accessibility shows banner | `PermissionBannerTest.p03_missingAccessibility_showsBanner` |
| P-04 | Missing overlay shows banner | `PermissionBannerTest.p04_missingOverlay_showsBanner` |
| P-05 | Missing two permissions lists both | `PermissionBannerTest.p05_missingTwo_listsBoth` |
| P-06 | Enforcement disabled shows turn-on CTA | `PermissionBannerTest.p06_enforcementDisabled_showsTurnOnCta` |
| P-07 | Last error shows dismiss banner | `PermissionBannerTest.p07_lastError_showsDismissBanner` |
| P-08 | Return from settings clears banner | `PermissionBannerTest.p08_returnFromSettings_clearsBanner` |
| P-09 | Onboarding permission refresh on resume | `OnboardingTest.ob02_getStarted_disabledUntilPermissions` |
| P-10 | Accessibility revoked triggers fallback path | `PermissionBannerTest.p10_accessibilityRevoked_showsBanner` |

---

## PERM — Permissions screen

| ID | Scenario | Automated test |
|----|----------|----------------|
| PERM-01 | Settings permissions screen stays visible | `PermissionsScreenTest.perm01_settingsPermissions_staysVisible` |

---

## L — Language

| ID | Scenario | Automated test |
|----|----------|----------------|
| L-01 | Default language tag on fresh settings | `LanguageSettingsTest.l01_defaultLanguageTag` |
| L-02 | Switch to each supported locale | `LanguageSettingsTest.l02_switchToEachSupportedLocale` |
| L-03 | Language persists after recreate | `LanguageSettingsTest.l03_languagePersistsAfterRecreate` |
| L-04 | Locale list shows native labels | `LanguageSettingsTest.l04_localeListShowsNativeLabels` |
| L-05 | Notification strings use app locale | `NotificationEnforcementTest.n11_localeChange_localizedNotificationText` |

---

## EN — Enforcement lifecycle

| ID | Scenario | Automated test |
|----|----------|----------------|
| EN-01 | Enforcement off hides service path | `EnforcementSettingsTest.en01_enforcementOff_updatesSettings` |
| EN-02 | Enforcement on persists | `EnforcementSettingsTest.en02_enforcementOn_persists` |
| EN-03 | Inactive profile not enforced | `TimeLimitEnforcementTest.tl_inactiveProfile_notEnforced` |
| EN-04 | Empty monitored list not enforced | `TimeLimitEnforcementTest.tl_emptyMonitored_notEnforced` |

---

## N — Notifications

| ID | Scenario | Automated test |
|----|----------|----------------|
| N-01 | Service notification while enforcement active | `NotificationEnforcementTest.n01_serviceNotification_present` |
| N-02 | Session timer hidden when no limits | `NotificationEnforcementTest.n02_sessionTimer_hiddenWhenNoLimits` |
| N-03 | Session timer shows session remaining | `NotificationEnforcementTest.n03_sessionTimer_showsSessionRemaining` |
| N-04 | Session timer shows daily/hourly/weekly lines | `NotificationEnforcementTest.n04_sessionTimer_showsUsageBuckets` |
| N-05 | Timer body dedup avoids flicker | `NotificationEnforcementTest.n05_timerBody_dedupNoFlicker` |
| N-06 | Eighty percent warning fires | `NotificationEnforcementTest.n06_eightyPercentWarning_fires` |
| N-07 | Tap notification opens MainActivity | `NotificationEnforcementTest.n07_tapNotification_opensMainActivity` |
| N-08 | Swipe session timer reappears | `NotificationEnforcementTest.n08_swipeSessionTimer_reappears` |
| N-09 | Swipe warning stays dismissed | `NotificationEnforcementTest.n09_swipeWarning_staysDismissed` |
| N-10 | Timer toggle off hides countdown | `NotificationEnforcementTest.n10_timerToggleOff_hidesCountdown` |
| N-11 | Locale change localizes notification | `NotificationEnforcementTest.n11_localeChange_localizedNotificationText` |
| N-12 | Screen off/on timer resumes | `NotificationEnforcementTest.n12_screenOffOn_timerResumes` |

---

## O — Overlay stability

| ID | Scenario | Automated test |
|----|----------|----------------|
| O-01 | Block within timeout after launch | `OverlayStabilityTest.o01_blockWithinTimeoutAfterLaunch` |
| O-02 | Switch monitored apps overlay follows | `OverlayStabilityTest.o02_switchApps_overlayFollows` |
| O-03 | Unmonitored app hides overlay | `OverlayStabilityTest.o03_unmonitoredApp_hidesOverlay` |
| O-04 | Same overlay updates without flicker | `OverlayStabilityTest.o04_sameOverlay_updatesWithoutFlicker` |
| O-05 | Friction in progress preserved on switch | `OverlayStabilityTest.o05_frictionInProgress_preservedOnSwitch` |
| O-06 | Back button on overlay | `OverlayStabilityTest.o06_backButton_onOverlay` |
| O-07 | Home then reopen shows overlay | `OverlayStabilityTest.o07_homeThenReopen_showsOverlay` |
| O-08 | Rotation during block survives | `OverlayStabilityTest.o08_rotationDuringBlock_survives` |
| O-09 | Delay-open countdown then dismisses | `OverlayStabilityTest.o09_delayOpenCountdown_dismisses` |
| O-10 | Extension buttons by surface mode | `OverlayStabilityTest.o10_extensionButtons_bySurfaceMode` |
| O-11 | Open Gatekeep from overlay | `OverlayStabilityTest.o11_openGatekeepFromOverlay` |
| O-12 | Keyboard on math challenge usable | `OverlayStabilityTest.o12_keyboardOnMathChallenge_usable` |

---

## G — Friction / open gates

| ID | Scenario | Automated test |
|----|----------|----------------|
| G-01 | Math wrong answer stays blocked | `OpenGateTest.g01_mathWrong_staysBlocked` |
| G-02 | Math correct proceeds | `OpenGateTest.g02_mathCorrect_proceeds` |
| G-03 | Wait timer completes | `OpenGateTest.g03_waitTimer_completes` |
| G-04 | Wait cancelled by leaving app | `OpenGateTest.g04_waitCancelled_byLeavingApp` |
| G-05 | Hold button duration | `OpenGateTest.g05_holdButton_duration` |
| G-06 | Type phrase exact match | `OpenGateTest.g06_typePhrase_exactMatch` |
| G-07 | Password on overlay | `OpenGateTest.g07_password_onOverlay` |
| G-08 | Difficulty affects math | `OpenGateTest.g08_difficulty_affectsMath` |
| G-09 | Open vs session wait durations | `OpenGateTest.g09_openVsSessionWait_durations` |
| G-10 | None friction with extension bypass | `OpenGateTest.g10_noneFriction_extensionBypass` |

---

## POL — Polling / timing

| ID | Scenario | Automated test |
|----|----------|----------------|
| POL-01 | First eval within 2s of launch | `PollingConsistencyTest.pol01_firstEval_within2s` |
| POL-02 | Same-app resume re-evaluates | `PollingConsistencyTest.pol02_sameAppResume_reevaluates` |
| POL-03 | Near limit blocks within 1s loop | `PollingConsistencyTest.pol03_nearLimit_blocksWithin1s` |
| POL-04 | Far limit blocks before 30s poll | `PollingConsistencyTest.pol04_farLimit_blocksBefore30s` |
| POL-05 | Overlay showing break timer accurate | `PollingConsistencyTest.pol05_overlayBreakTimer_accurate` |
| POL-06 | Screen off pauses wait stopwatch | `PollingConsistencyTest.pol06_screenOff_pausesWaitStopwatch` |
| POL-07 | Rapid app switching no duplicate sessions | `PollingConsistencyTest.pol07_rapidSwitch_noDuplicateSessions` |

---

## TL — Time limits

| ID | Scenario | Automated test |
|----|----------|----------------|
| TL-01 | Session limit triggers session action | `TimeLimitEnforcementTest.tl01_sessionLimit_triggersAction` |
| TL-02 | Daily limit before session | `TimeLimitEnforcementTest.tl02_dailyLimit_beforeSession` |
| TL-03 | Hourly cap mid-session | `TimeLimitEnforcementTest.tl03_hourlyCap_midSession` |
| TL-04 | Weekly cap on last day | `TimeLimitEnforcementTest.tl04_weeklyCap_lastDay` |
| TL-05 | Shared pool counts across apps | `TimeLimitEnforcementTest.tl05_sharedPool_countsAcrossApps` |
| TL-06 | Multi-profile strictest cap | `MultiProfileEnforcementTest.mp01_twoProfiles_strictestCap` |
| TL-07 | Extension bonus past base cap | `TimeLimitEnforcementTest.tl07_extensionBonus_pastBaseCap` |
| TL-08 | No limit today hides limits | `TimeLimitEnforcementTest.tl08_noLimitToday_hidesLimits` |
| TL-09 | Gradual tightening reduces limit | `TimeLimitEnforcementTest.tl09_gradualTightening_reducesLimit` |
| TL-10 | Null limit field means no cap | `TimeLimitEnforcementTest.tl10_nullLimit_noCap` |

---

## PA — Pausing

| ID | Scenario | Automated test |
|----|----------|----------------|
| PA-01 | Profile pause allows app | `PauseScreenTest.pa01_profilePause_fromUi` |
| PA-02 | App-only pause | `PauseEnforcementTest.pa02_appOnlyPause_otherAppsLimited` |
| PA-03 | Multi-profile selective pause | `PauseEnforcementTest.pa03_multiProfile_selectivePause` |
| PA-04 | Global pause all profiles | `PauseScreenTest.pa04_globalPause_fromUi` |
| PA-05 | Profile pause today allows app | `PauseEnforcementTest.pa05_profilePauseToday_allowsOverCap` |
| PA-06 | Profile pause today shared pool | `PauseEnforcementTest.pa06_profilePauseToday_sharedPool` |
| PA-07 | Pause expires while in foreground | `PauseEnforcementTest.pa07_pauseExpires_whileInForeground` |
| PA-08 | Pause overrides schedule block | `PauseEnforcementTest.pa08_pauseOverrides_scheduleBlock` |
| PA-09 | Focus mode blocks | `PauseEnforcementTest.pa09_focusMode_blocks` |
| PA-10 | Focus block profile scoped | `PauseEnforcementTest.pa10_focusBlock_profileScoped` |
| PA-11 | End pause early from UI | `PauseScreenTest.pa11_endPauseEarly_fromUi` |
| PA-12 | Overlapping pauses | `PauseEnforcementTest.pa12_overlappingPauses` |

---

## PP — Profile PIN (open gate)

| ID | Scenario | Automated test |
|----|----------|----------------|
| PP-01 | Wrong profile PIN stays blocked | `OpenGateTest.pp01_wrongProfilePin_staysBlocked` |
| PP-02 | Correct profile PIN proceeds | `OpenGateTest.pp02_correctProfilePin_proceeds` |
| PP-03 | Switch app requires PIN again | `OpenGateTest.pp03_switchApp_requiresPinAgain` |
| PP-04 | App PIN then profile PIN stack | `OpenGateTest.pp04_appPinThenProfilePin_stack` |
| PP-05 | Enable profile PIN in editor | `ProfilePinScreenTest.pp05_enableProfilePin_inEditor` |
| PP-06 | PIN gate with schedule block | `OpenGateTest.pp06_pinGateWithScheduleBlock` |

---

## S — Schedules

| ID | Scenario | Automated test |
|----|----------|----------------|
| S-01 | Allow window limits apply | `ScheduleEnforcementTest.s01_allowWindow_limitsApply` |
| S-02 | Block window schedule block | `ScheduleEnforcementTest.s02_blockWindow_scheduleBlock` |
| S-03 | Outside window no-match behavior | `ScheduleEnforcementTest.s03_outsideWindow_noMatchBehavior` |
| S-04 | Customize segment overrides session | `ScheduleEnforcementTest.s04_customize_overridesSession` |
| S-05 | Segment boundary at midnight | `ScheduleEnforcementTest.s05_segmentBoundary_midnight` |
| S-06 | Overlapping segments sort order | `ScheduleEnforcementTest.s06_overlappingSegments_sortOrder` |
| S-07 | Inactive segment ignored | `ScheduleEnforcementTest.s07_inactiveSegment_ignored` |
| S-08 | Auto schedule UI smoke | `ScheduleEnforcementTest.s08_autoSchedule_smoke` |
| S-09 | Schedule block beats open gate | `ScheduleEnforcementTest.s09_scheduleBlock_beatsOpenGate` |
| S-10 | Copy segment label in enforcement | `ScheduleEnforcementTest.s10_copySegment_labelInEnforcement` |

---

## R — Rules crosses (emulator golden paths)

| ID | Scenario | Automated test |
|----|----------|----------------|
| R-01 | none + hardBlock + notifyOnly | `RulesCrossEnforcementTest.r01_none_hardBlock_notifyOnly` |
| R-02 | pinGate + limitWithExtensions + mandatoryBreak | `RulesCrossEnforcementTest.r02_pinGate_extensions_mandatoryBreak` |
| R-03 | deterrentWait + deterrentMath + hardBlock | `RulesCrossEnforcementTest.r03_deterrentWait_math_hardBlock` |
| R-04 | deterrentMath + mandatoryBreak + limitWithExtensions | `RulesCrossEnforcementTest.r04_deterrentMath_break_extensions` |
| R-05 | none + notifyOnly + hardBlock | `RulesCrossEnforcementTest.r05_none_notify_hardBlock` |
| R-06 | pinGate + hardBlock + notifyOnly | `RulesCrossEnforcementTest.r06_pinGate_hardBlock_notifyOnly` |
| R-07 | deterrentWait + limitWithExtensions + mandatoryBreak | `RulesCrossEnforcementTest.r07_wait_extensions_break` |
| R-08 | none + limitWithExtensions + hardBlock | `RulesCrossEnforcementTest.r08_none_extensions_hardBlock` |
| R-09 | pinGate + notifyOnly + limitWithExtensions | `RulesCrossEnforcementTest.r09_pinGate_notify_extensions` |
| R-10 | deterrentMath + hardBlock + mandatoryBreak | `RulesCrossEnforcementTest.r10_math_hardBlock_break` |
| R-11 | notifyOnly no overlay on limit | `RulesCrossEnforcementTest.r11_notifyOnly_noOverlayOnLimit` |
| R-12 | hardBlock no bypass | `RulesCrossEnforcementTest.r12_hardBlock_noBypass` |
| R-13 | limitWithExtensions shows extension buttons | `RulesCrossEnforcementTest.r13_limitWithExtensions_showsButtons` |
| R-14 | mandatoryBreak shows countdown | `RulesCrossEnforcementTest.r14_mandatoryBreak_showsCountdown` |
| R-15 | noScheduleMatch open override | `RulesCrossEnforcementTest.r15_noScheduleMatch_openOverride` |
| R-16 | noScheduleMatch limit override | `RulesCrossEnforcementTest.r16_noScheduleMatch_limitOverride` |
| R-17 | noScheduleMatch session override | `RulesCrossEnforcementTest.r17_noScheduleMatch_sessionOverride` |
| R-18 | customize segment limit action | `RulesCrossEnforcementTest.r18_customizeSegment_limitAction` |
| R-19 | open gate none skips friction | `RulesCrossEnforcementTest.r19_openNone_skipsFriction` |
| R-20 | session notify only allowed flag | `RulesCrossEnforcementTest.r20_sessionNotify_allowedFlag` |

Full combinatorial matrix: `RulesMatrixTest` (JVM) + `RulesInteractionTest` (JVM combined triples).

### R-INT — Rule interaction (open / session / period orthogonality)

| ID | Scenario | Automated test | Status |
|----|----------|----------------|--------|
| R-INT-01 | Open wait + daily hardBlock skips open wait | `RulesCrossEnforcementTest.rInt01_openWait_dailyHardBlock_skipsOpenWait` | pass |
| R-INT-02 | Open wait before daily extension block | `RulesCrossEnforcementTest.rInt02_openWait_dailyExtensions_openFirst` | pass |
| R-INT-03 | Open wait under limits then proceed | `RulesCrossEnforcementTest.rInt03_openWait_underLimits_proceeds` | weak |
| R-INT-04 | noLimitToday bypasses session hardBlock for today | `RulesCrossEnforcementTest.rInt04_noLimitToday_bypassesSessionHardBlockForToday` | pass |
| R-INT-06 | noLimitToday bypasses period only | `RulesCrossEnforcementTest.rInt06_noLimitToday_periodBypass_noDailyBlock` | weak (engine pass; UI overlay parity) |

See also `docs/test-run-report.md` for latest run summary.

---

## E — Extensions

| ID | Scenario | Automated test |
|----|----------|----------------|
| E-01 | Grant from overlay extends session | `ExtensionEnforcementTest.e01_grantFromOverlay_extendsSession` |
| E-02 | Grant from in-app current usage | `CurrentUsageActionsTest.cu02_extendSession_inApp` |
| E-03 | Max extensions per day denied | `ExtensionEnforcementTest.e03_maxPerDay_denied` |
| E-04 | Max consecutive denied | `ExtensionEnforcementTest.e04_maxConsecutive_denied` |
| E-05 | No limit today override | `ExtensionEnforcementTest.e05_noLimitToday_override` |
| E-06 | Extension grace pause | `ExtensionEnforcementTest.e06_extensionGrace_pause` |
| E-07 | Surface overlay only | `ExtensionEnforcementTest.e07_surfaceOverlayOnly` |
| E-08 | Custom minutes extension | `ExtensionEnforcementTest.e08_customMinutes_extension` |
| E-09 | Extension after hard block denied | `ExtensionEnforcementTest.e09_afterHardBlock_denied` |
| E-10 | Overlay +5m over daily cap dismisses overlay, no re-block loop | `ExtensionGraceEnforcementTest.e10_overlayExtend5_overDailyCap_dismissesOverlayAndStaysAllowed` |
| E-11 | Over-cap usage + grace evaluates allowed | `ExtensionGraceEnforcementTest.e11_overDailyCap_programmaticGrace_evaluatesAllowed` |
| E-12 | Grace over daily cap: HUD shows used / base+bonus (not ∞ or 289h-style) | `ExtensionGraceEnforcementTest.e12_overDailyCap_graceCountdown_showsFiniteLimitNotInfinity` |
| E-13 | Overlay no-limit-today dismisses overlay | `ExtensionGraceEnforcementTest.e13_overlayNoLimitToday_dismissesOverlayAndStaysAllowed` |
| E-14 | Overlay no-limit-today uses session policy when limit policy disables | `ExtensionGraceEnforcementTest.e14_overlayNoLimitToday_sessionPolicyUsedWhenLimitPolicyDisables` |

---

## CU — Current usage buttons

| ID | Scenario | Automated test |
|----|----------|----------------|
| CU-01 | Extend daily limit in-app | `CurrentUsageActionsTest.cu01_extendDaily_inApp` |
| CU-02 | Extend session in-app | `CurrentUsageActionsTest.cu02_extendSession_inApp` |
| CU-03 | Reset usage in-app | `CurrentUsageActionsTest.cu03_resetUsage_inApp` |
| CU-04 | Shared pool extend all apps | `CurrentUsageActionsTest.cu04_sharedPool_extendAllApps` |
| CU-05 | Per-app extend only package | `CurrentUsageActionsTest.cu05_perApp_extendOnlyPackage` |
| CU-06 | Snackbar debounce on rapid taps | `CurrentUsageActionsTest.cu06_snackbar_debounceRapidTaps` |
| CU-07 | Extension denied shows feedback | `CurrentUsageActionsTest.cu07_extensionDenied_feedback` |
| CU-08 | Disabled when enforcement off | `CurrentUsageActionsTest.cu08_disabledWhenEnforcementOff` |
| CU-09 | No limit today in-app with overlay-only policy shows ∞ + Applied | `CurrentUsageActionsTest.cu09_noLimitToday_overlayOnlyPolicy_showsInfinityAndApplied` |
| CU-10 | No limit today shared pool shows ∞ in Current Usage | `CurrentUsageActionsTest.cu10_noLimitToday_sharedPool_showsInfinity` |
| CU-11 | No limit today in-app works when session policy disables overlay option | `CurrentUsageActionsTest.cu11_noLimitToday_limitPolicyUsedWhenSessionPolicyDisables` |
| CU-12 | Extend minutes in-app ignores overlay-only surface and quotas | `CurrentUsageActionsTest.cu12_extendMinutes_overlayOnlyPolicy_showsApplied` |

---

## MP — Multi-profile merge

| ID | Scenario | Automated test |
|----|----------|----------------|
| MP-01 | Two profiles strictest cap | `MultiProfileEnforcementTest.mp01_twoProfiles_strictestCap` |
| MP-02 | One profile paused one active | `MultiProfileEnforcementTest.mp02_onePaused_oneActive` |
| MP-03 | Different schedule segments same app | `MultiProfileEnforcementTest.mp03_differentSchedules_sameApp` |

---

## SET — Settings smoke

| ID | Scenario | Automated test |
|----|----------|----------------|
| SET-01 | Session timer toggle | `EnforcementSettingsTest.set01_sessionTimerToggle` |
| SET-02 | Weekly report toggle | `EnforcementSettingsTest.set02_weeklyReportToggle` |
| SET-03 | HUD toggle linked to timer | `EnforcementSettingsTest.set03_hudToggle_linkedToTimer` |

---

## ST — Stats smoke

| ID | Scenario | Automated test |
|----|----------|----------------|
| ST-01 | Stats screen loads | `StatsScreenSmokeTest.st01_statsScreen_loads` |
| ST-02 | Period swipe changes range | `StatsScreenSmokeTest.st02_periodSwipe_changesRange` |
| ST-03 | Bar chart with seeded usage | `StatsScreenSmokeTest.st03_barChart_seededUsage` |

---

## SYS — System integration

| ID | Scenario | Automated test |
|----|----------|----------------|
| SYS-01 | Launcher target home recents chain | `SystemIntegrationTest.sys01_launcherTargetHomeRecents` |
| SYS-02 | Settings foreground no crash | `SystemIntegrationTest.sys02_settingsForeground_noCrash` |
| SYS-03 | Split screen optional | `SystemIntegrationTest.sys03_splitScreen_optional` |

---

## MAN — Manual only

| ID | Scenario | Automated test |
|----|----------|----------------|
| MAN-01 | PIN-05 screen lock wake | `ManualScenariosTest.pin05_screenLockRequiresPinOnWake` |
| MAN-02 | PIN-06 force-stop relaunch | `ManualScenariosTest.pin06_forceStopRequiresPinOnRelaunch` |
| MAN-03 | OEM battery autostart | `ManualScenariosTest.man03_oemBatteryAutostart` |
| MAN-04 | Real device overlay flicker | `ManualScenariosTest.man04_realDeviceOverlayFlicker` |

---

## Implementation notes

- Lock logic lives in `AppLockController` (unit tested).
- Cross-app tests use `@LargeTest`, `EnforcementTestHarness`, and debug `EnforcementTargetActivity`.
- Instrumented tests seed repositories then drive UI or launch target apps via shell.
- Rules matrix combinatorics run in `RulesMatrixTest` / `SchedulePolicyMatrixTest` on JVM.
