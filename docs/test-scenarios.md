# Gatekeep test scenarios

Regression catalog for automated and manual testing. Each scenario ID maps to one or more tests. **Lane:** JVM = RuleEngine / classifier / copy (seconds). Connected = WindowManager, overlay chrome, Compose, notifications. An ID can keep its number after moving from connected to JVM.

## Running tests

Process (yield gate, ship bar, fail dumps, waits): [instrumented-testing.md](instrumented-testing.md).

```bash
cd Gatekeep
export JAVA_HOME="../.tools/jdk-17.0.14+7"
export ANDROID_HOME="../.tools/android-sdk"

# JVM unit tests (fast) — always first
./gradlew :core-domain:test :core-data:test :app:testDebugUnitTest

# Ship-bar emulator gate (@E2eGate methods, under 10 min). Start AVD first.
./gradlew :app:connectedDebugAndroidTest

# Opt-in overlay combinatorics (not the ship bar; hour-long)
./gradlew :app:connectedDebugAndroidTest -Pgatekeep.e2eGate=false \
  -Pandroid.testInstrumentationRunnerArguments.class=com.gatekeep.app.enforcement.ExtensionEnforcementTest

# Single class
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.gatekeep.app.enforcement.OverlayStabilityTest

# Skip slow cross-app tests (@LargeTest)
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.notAnnotation=androidx.test.filters.LargeTest
```

**Prerequisites:** Android emulator running (`Medium_Phone_API_36.0` or similar), `local.properties` with `sdk.dir`, debug keystore at `keystore/debug.keystore`.

Gradle uses JDK 21 via `org.gradle.java.home` in `gradle.properties` (Studio JBR).

### After any enforcement change (minimum)

```bash
cd Gatekeep
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"   # Studio laptop
export ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"

./gradlew :core-domain:test :core-data:test :app:testDebugUnitTest
# Emulator running — @E2eGate only (under 10 min), not overlay combinatorics:
./gradlew :app:connectedDebugAndroidTest
```

### Enforcement decision trace (emulator + phone)

**Debug** builds log every major coordinator decision to logcat tag **`GatekeepEnforcement`**
(`evaluate`, `unlock_skip`, `unlock_resume`, `open_gate_passed`, `pending_wait_resume`, `show_open_deterrent`, …).

The **`dist/gatekeep-*.apk` on your phone is a release (minified) build** — it does **not** write that tag to logcat.
The in-memory buffer is still filled, persisted to `enforcement-trace.log`, and exportable from **Settings → About → Copy enforcement log** (choose a time range first). Repeated identical `foreground_commit` lines are coalesced.
To also stream traces live, install a debug APK:
`./gradlew :app:assembleDebug` then `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

Run connected tests against the emulator only (`ANDROID_SERIAL=emulator-5554`) so the phone is not included.

```bash
adb logcat -s GatekeepEnforcement
# or clear and capture a repro:
adb logcat -c && adb logcat -s GatekeepEnforcement
```

**Release-only crash:** if open-wait finishes then Gatekeep dies (double overlay flash), check
`adb logcat -b crash` for `NoSuchMethodError` on `SessionState.copy` / `OpenGatePassPolicy.markOpenGatePassed`.
That is a **stale incremental release build**, not a logic regression — rebuild with `scripts/build_apk.sh`
(which runs `clean` + `:core-domain:test` + `assembleRelease`) and reinstall.

Instrumented tests can assert the same buffer via `EnforcementCoordinator.enforcementTraceCount(...)`
(see `OpenGateTest.g16_openWaitComplete_noSecondWaitOrSessionPendingResume`).

`EnforcementLog` in SharedPreferences only stores the **last crash/error** string for the in-app banner — not a full trace.

---

## PIN — App lock

| ID | Scenario | Automated test |
|----|----------|----------------|
| PIN-01 | Cold start with lock enabled | `MainActivityAppLockTest.pin01_coldStart_showsLockScreen` |
| PIN-02 | Correct PIN unlocks to dashboard | `AppLockScreenTest.pin02_*` |
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
| P-03 | Missing accessibility shows banner | JVM `PermissionStateMissingFlagTest` (missing accessibility) |
| P-04 | Missing overlay shows banner | JVM `PermissionStateMissingFlagTest` (missing overlay) |
| P-05 | Missing two permissions lists both | JVM `PermissionStateMissingFlagTest` (missing usage and accessibility) |
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
| L-02 | Switch to each supported locale | JVM `StringsResourceParityTest`; persist path is `LanguageSettingsTest.l03_*` |
| L-03 | Language persists after recreate | `LanguageSettingsTest.l03_languagePersistsAfterRecreate` |
| L-04 | Locale list shows native labels | JVM `StringsResourceParityTest` |
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
| N-01 | FGS service notification only when accessibility is down | `NotificationEnforcementTest.n01_serviceNotification_present` |
| N-02 | Session timer hidden when no limits | `NotificationEnforcementTest.n02_sessionTimer_hiddenWhenNoLimits` |
| N-03 | Session timer shows session remaining | `NotificationEnforcementTest.n03_sessionTimer_showsSessionRemaining` |
| N-04 | Session timer shows daily/hourly/weekly lines | JVM `UsageHudLinesTest`; connected smoke `NotificationEnforcementTest.n03_*` |
| N-05 | Timer body dedup avoids flicker | JVM `UsageHudLinesTest`; connected smoke `NotificationEnforcementTest.n03_*` |
| N-06 | Eighty percent warning fires | `NotificationEnforcementTest.n06_eightyPercentWarning_fires` |
| N-07 | Tap notification opens MainActivity | `NotificationEnforcementTest.n07_tapNotification_opensMainActivity` |
| N-08 | Swipe session timer reappears | `NotificationEnforcementTest.n08_swipeSessionTimer_reappears` |
| N-09 | Swipe warning stays dismissed | `NotificationEnforcementTest.n09_swipeWarning_staysDismissed` |
| N-10 | Timer toggle off hides countdown | `NotificationEnforcementTest.n10_timerToggleOff_hidesCountdown` |
| N-11 | Locale change localizes notification | JVM `StringsResourceParityTest`; persist path `LanguageSettingsTest.l03_*` |
| N-12 | Screen off/on timer resumes | JVM `DeviceUsePolicyTest`; connected smoke `NotificationEnforcementTest.n03_*` |

---

## O — Overlay stability

| ID | Scenario | Automated test |
|----|----------|----------------|
| O-01 | Block within timeout after launch | `ScreenObservationTest.obs01_*` (same 2s overlay-show claim) |
| O-02 | Switch monitored apps overlay follows | `OverlayStabilityTest.o02_switchApps_overlayFollows` |
| O-03 | Unmonitored app hides overlay | `OverlayStabilityTest.o03_unmonitoredApp_hidesOverlay` |
| O-04 | Same overlay updates without flicker | `ScreenObservationTest.obs03_*` |
| O-05 | Friction in progress preserved on switch | `OverlayStabilityTest.o05_frictionInProgress_preservedOnSwitch` |
| O-06 | Back button on overlay | `OverlayStabilityTest.o06_backButton_onOverlay` |
| O-07 | Home then reopen shows overlay | `ScreenObservationTest.obs02_*` + `OverlayStabilityTest.o13_*` / `o14_*` |
| O-08 | Rotation during block survives | `OverlayStabilityTest.o08_rotationDuringBlock_survives` |
| O-09 | Delay-open countdown then dismisses | `OverlayStabilityTest.o09_delayOpenCountdown_dismisses` |
| O-10 | Extension buttons by surface mode | `OverlayStabilityTest.o10_extensionButtons_bySurfaceMode` |
| O-11 | Open Gatekeep from overlay | `OverlayStabilityTest.o11_openGatekeepFromOverlay` |
| O-12 | Keyboard on math challenge usable | `OverlayStabilityTest.o12_keyboardOnMathChallenge_usable` |
| O-13 | Overview button hides overlay | `OverlayStabilityTest.o13_overviewButton_hidesOverlay` |
| O-14 | Overview then same app reshows overlay | `OverlayStabilityTest.o14_overviewThenSameApp_reshowsOverlay` |
| O-15 | Overview then tap recents card reshows overlay | `OverlayStabilityTest.o14_*` |
| O-16 | Stale recents window stack restores overlay | JVM `OverlayWindowClassifierTest` |
| O-17 | Instagram overview class does not hide overlay | JVM `OverlayWindowClassifierTest` |
| O-18 | Home or Recents hides overlay without a flicker | `ScreenObservationTest.obs02_*` + `OverlayStabilityTest.o13_*` |

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
| G-08 | Difficulty affects math | JVM `OverlayFrictionDifficultyTest` |
| G-09 | Open vs session wait durations | `OpenGateTest.g09_openVsSessionWait_durations` |
| G-10 | None friction with extension bypass | `OpenGateTest.g10_noneFriction_extensionBypass` |
| G-11 | Same-package activity change does not reopen gate | `OpenGateTest.g11_samePackageActivityChange_doesNotReopenGate` |
| G-12 | Leave and return reopens gate after grace expires | `OpenGateTest.g12_leaveAndReturn_reopensGate` |
| G-13 | Quick return within session continuity grace (default 1 min) skips open gate | `OpenGateTest.g13_quickReturn_withinGrace_skipsOpenGate` |
| G-14 | Launcher blip does not reopen open gate | `OpenGateTest.g14_spuriousLauncherBlip_doesNotReopenGate` |
| G-15 | Quick bounce without passing still shows open gate | `OpenGateTest.g15_quickBounce_neverPassed_stillShowsOpenGate` |
| G-16 | Open wait completes once — no second wait / session pending resume | `OpenGateTest.g16_openWaitComplete_noSecondWaitOrSessionPendingResume` |
| G-16 | Same-package event re-evaluates when open gate not passed | `ForegroundTransitionPolicyTest.samePackage_reevaluatesWhenOpenGateNotPassed` |

---

## POL — Polling / timing

| ID | Scenario | Automated test |
|----|----------|----------------|
| POL-01 | First eval within 2s of launch | `ScreenObservationTest.obs01_*` |
| POL-02 | Same-app resume re-evaluates | `ScreenObservationTest.obs02_*` + `ResumeEnforcementTest.res02_*` |
| POL-03 | Near limit blocks within 1s loop | `PollingConsistencyTest.pol03_nearLimit_blocksWithin1s` |
| POL-04 | Far limit blocks before 30s poll | `PollingConsistencyTest.pol03_*` (near-limit SLA); far poll is not a separate connected case |
| POL-05 | Overlay showing break timer accurate | `PollingConsistencyTest.pol05_overlayBreakTimer_accurate` |
| POL-06 | Screen off resets in-progress wait | `ScreenObservationTest.obs04_*` / `obs05_*` |
| POL-07 | Rapid app switching no duplicate sessions | `PollingConsistencyTest.pol07_rapidSwitch_noDuplicateSessions` |

---

## OBS — On-screen overlay timing

These cases launch with `am start` and read `block_message` / `wait_countdown`. They do not inject a foreground change. A failure records the visible package, overlay text, countdown, and any Gatekeep fatal crash.

| ID | Scenario | Automated test |
|----|----------|----------------|
| OBS-01 | Hard block with no injected foreground shows within 2s | `ScreenObservationTest.obs01_hardBlock_noInjection_overlayWithin2s` |
| OBS-02 | Home hides the overlay within 1.5s | `ScreenObservationTest.obs02_home_overlayGoneWithin1500ms` |
| OBS-03 | Blocked overlay stays up and the message does not flicker | `ScreenObservationTest.obs03_blockedOverlay_doesNotFlicker` |
| OBS-08 | Open-gate wait with no injected foreground shows within 2s | `ScreenObservationTest.obs08_openWait_noInjection_overlayWithin2s` |
| OBS-04 | Open-wait countdown resets when the screen is off | `ScreenObservationTest.obs04_openWait_resetsOnScreenOff` |
| OBS-05 | Session-limit wait resets when the screen is off | `ScreenObservationTest.obs05_sessionWait_resetsOnScreenOff` |
| OBS-06 | Home past session-continuity grace starts a fresh session | `ScreenObservationTest.obs06_*` (injects `SessionContinuityGrace.MIN_MS`); 60s default math is JVM `SessionStartDecisionTest` |
| OBS-07 | Home for about 5s keeps the session and excludes the gap | `ScreenObservationTest.obs07_leaveFiveSeconds_sameSessionGapExcluded` |
| OBS-09 | Home without opening another app freezes the session clock within 1s | `ScreenObservationTest.obs09_home_freezesSessionWithoutOtherApp` |

---

## TL — Time limits

| ID | Scenario | Automated test |
|----|----------|----------------|
| TL-01 | Session limit triggers session action | `TimeLimitEnforcementTest.tl01_sessionLimit_triggersAction` |
| TL-02 | Daily limit before session | `TimeLimitEnforcementTest.tl02_dailyLimit_beforeSession` |
| TL-03 | Hourly cap mid-session | JVM `PeriodContinuePolicyTest` / `OverlayChromePolicyTest`; overlay chrome is `tl02_*` |
| TL-04 | Weekly cap on last day | JVM `PeriodContinuePolicyTest` / `OverlayChromePolicyTest`; overlay chrome is `tl02_*` |
| TL-05 | Shared pool counts across apps | `TimeLimitEnforcementTest.tl05_sharedPool_countsAcrossApps` |
| TL-06 | Multi-profile strictest cap | `MultiProfileEnforcementTest.mp01_twoProfiles_strictestCap` |
| TL-07 | Extension bonus past base cap | `TimeLimitEnforcementTest.tl07_extensionBonus_pastBaseCap` |
| TL-08 | No limit today hides limits | `TimeLimitEnforcementTest.tl08_noLimitToday_hidesLimits` |
| TL-09 | Gradual tightening reduces limit | `TimeLimitEnforcementTest.tl09_gradualTightening_reducesLimit` |
| TL-10 | Null limit field means no cap | `TimeLimitEnforcementTest.tl10_nullLimit_noCap` |
| TL-15 | Stale overnight session starts fresh, not timeout | JVM `SessionStartDecisionTest` |
| TL-16 | Process death / null lastForegroundEnd starts a new session | `SessionStartDecisionTest.processDeath_nullLastEnd_startsFresh` |
| TL-17 | Session start before 4am day reset starts fresh when not live | `SessionStartDecisionTest.processDeath_sessionBeforeDayStart_startsFresh` |
| TL-18 | Live in-memory session continues across 4am | `SessionStartDecisionTest.liveInMemory_acrossDayStart_continues` |
| TL-19 | Away under 1 min resumes same session | `SessionStartDecisionTest.notLive_gapUnderOneMinute_sameDay_resumes` |
| TL-20 | Away 1 min or more starts a new session | `SessionStartDecisionTest.notLive_gapAtLeastOneMinute_startsFresh` |
| TL-21 | Live in-memory session starts fresh after 1 min away | `SessionStartDecisionTest.liveInMemory_gapAtLeastOneMinute_startsFresh` |
| TL-22 | Away time does not consume session remaining | `SessionTrackerTest.away time after leave does not consume session remaining` |
| TL-23 | Away under 1 min does not expire a session that would fail on wall clock | JVM `SessionStartDecisionTest`; device gap is `ScreenObservationTest.obs07_*` |
| TL-24 | Calendar hour boundary with 4am day reset: zero stats must not wipe usage / HUD | `UsageSnapshotResolverTest.mergePeriodMs keeps persisted when stats report zero`, `UsageSnapshotResolverTest.applyLivePeriodMs holds displayed usage when stats briefly zero`, `HudUsageDisplayTest.mergeWithPersisted_usesPersistedWhenStatsZero` |

---

## RES — Resume without foreground change

| ID | Scenario | Automated test |
|----|----------|----------------|
| RES-01 | Screen unlock into same monitored app re-runs open gate or HUD | `ResumeEnforcementTest.res01_screenUnlockSameApp_runsOpenGateOrCountdown`, `MonitoredForegroundResumePolicyTest` |
| RES-02 | Session extension overlay returns after home → reopen | `ResumeEnforcementTest.res02_sessionExtensionOverlay_returnsAfterHome`, `BlockClearOnAllowedPolicyTest`, `LimitOverlayHidePolicyTest` |
| RES-03 | Reopen after session limit still blocked (no free usage) | `ResumeEnforcementTest.res03_sessionExtensionOverlay_reopenStillBlocked`, `DomainTests.session at cap stays exceeded while limit block friction is active` |
| RES-04 | Screen off longer than session remaining does not expire the session | `ResumeEnforcementTest.res04_screenOffLongerThanRemaining_doesNotExpireSession`, `DeviceUsePolicyTest`, `DomainTests.device pause freezes remaining across wall-clock pocket time` |
| RES-05 | SCREEN_ON onto lock screen does not pass open gate; unlock does | `ResumeEnforcementTest.res05_wakeToLockScreen_openGateRunsOnUnlock`, `DeviceUsePolicyTest.screenOnWhileLocked_doesNotResumeEvaluate` |
| RES-06 | Lock past session-continuity grace starts a fresh session | `ResumeEnforcementTest.customGrace_lockPastFifteenSeconds_startsFresh` (injects `MIN_MS`); 60s default math is JVM `SessionStartDecisionTest` |
| RES-07 | Lock ~5s mid-session continues the same session with remaining frozen | `ResumeEnforcementTest.res07_shortLock_continuesSameSessionRemainingFrozen` |
| RES-08 | Unlock on home / launcher must not flash open wait for the last monitored app | `ResumeEnforcementTest.res08_unlockOnHome_doesNotFlashOpenWait`, `UnlockOverlayPolicyTest` |

---

## PA — Pausing

| ID | Scenario | Automated test |
|----|----------|----------------|
| PA-01 | Profile pause allows app | `PauseScreenTest.pa01_profilePause_fromUi` |
| PA-02 | App-only pause | `PauseEnforcementTest.pa02_appOnlyPause_otherAppsLimited` |
| PA-03 | Multi-profile selective pause | JVM `FocusBlockManagerTest` / `PauseManager` profile scope; device smoke is `pa02_*` / `mp02_*` |
| PA-04 | Global pause all profiles | `PauseScreenTest.pa04_globalPause_fromUi` |
| PA-05 | Profile pause today allows app | `PauseEnforcementTest.pa05_profilePauseToday_allowsOverCap` |
| PA-06 | Profile pause today shared pool | `PauseEnforcementTest.pa05_*` + JVM `PauseManagerNoLimitTodayTest` |
| PA-07 | Pause expires while in foreground | `PauseEnforcementTest.pa07_pauseExpires_whileInForeground` |
| PA-08 | Pause overrides schedule block | `PauseEnforcementTest.pa08_pauseOverrides_scheduleBlock` |
| PA-09 | Focus mode blocks | `PauseEnforcementTest.pa09_focusMode_blocks`; JVM `RuleEngineTest.settings focus mode until blocks monitored app` |
| PA-10 | Focus block profile scoped | JVM `FocusBlockManagerTest.profile focus block does not apply to other profiles`; device smoke `pa09_*` |
| PA-11 | End pause early from UI | `PauseScreenTest.pa11_endPauseEarly_fromUi` |
| PA-12 | Overlapping pauses | JVM `PauseManagerOverlapTest` |

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
| S-01 | Allow window limits apply | `ScheduleEnforcementTest.s08_autoSchedule_smoke` |
| S-02 | Block window schedule block | `ScheduleEnforcementTest.s02_blockWindow_scheduleBlock` |
| S-03 | Outside window no-match behavior | JVM `SchedulePolicyMatrixTest` |
| S-04 | Customize segment overrides session | `ScheduleEnforcementTest.s04_customize_overridesSession` |
| S-05 | Segment boundary at midnight | JVM `SchedulePolicyResolverTest` / `SchedulePolicyMatrixTest` |
| S-06 | Overlapping segments sort order | JVM `SchedulePolicyResolverTest` / `SchedulePolicyMatrixTest` |
| S-07 | Inactive segment ignored | JVM `SchedulePolicyResolverTest` |
| S-08 | Auto schedule UI smoke | `ScheduleEnforcementTest.s08_autoSchedule_smoke` |
| S-09 | Schedule block beats open gate | `ScheduleEnforcementTest.s09_scheduleBlock_beatsOpenGate` |
| S-10 | Copy segment label in enforcement | `ScheduleEnforcementTest.s10_copySegment_labelInEnforcement` |

---

## R — Rules crosses (JVM matrix; connected chrome only)

| ID | Scenario | Automated test |
|----|----------|----------------|
| R-01 | none + hardBlock + notifyOnly | JVM `RulesMatrixTest` (`none_hard_notify`) |
| R-02 | pinGate + limitWithExtensions + mandatoryBreak | JVM `RulesMatrixTest` (`pin_ext_break`) |
| R-03 | deterrentWait + deterrentMath + hardBlock | JVM `RulesMatrixTest` (`wait_math_hard`) |
| R-04 | deterrentMath + mandatoryBreak + limitWithExtensions | JVM `RulesMatrixTest` (`math_break_ext`) |
| R-05 | none + notifyOnly + hardBlock | JVM `RulesMatrixTest` (`none_notify_hard`) |
| R-06 | pinGate + hardBlock + notifyOnly | JVM `RulesMatrixTest` (`pin_hard_notify`) |
| R-07 | deterrentWait + limitWithExtensions + mandatoryBreak | JVM `RulesMatrixTest` (`wait_ext_break`) |
| R-08 | none + limitWithExtensions + hardBlock | JVM `RulesMatrixTest` (`none_ext_hard`) |
| R-09 | pinGate + notifyOnly + limitWithExtensions | JVM `RulesMatrixTest` (`pin_notify_ext`) |
| R-10 | deterrentMath + hardBlock + mandatoryBreak | JVM `RulesMatrixTest` (`math_hard_break`) |
| R-11 | notifyOnly no overlay on limit | `RulesCrossEnforcementTest.r11_notifyOnly_noOverlayOnLimit` |
| R-12 | hardBlock no bypass | JVM `RulesMatrixTest` / `RuleEngineTest.hard block on limit disallows bypass` |
| R-13 | limitWithExtensions shows extension buttons | `RulesCrossEnforcementTest.r13_limitWithExtensions_showsButtons` |
| R-14 | mandatoryBreak shows countdown | `RulesCrossEnforcementTest.r14_mandatoryBreak_showsCountdown` |
| R-15 | noScheduleMatch open override | JVM `SchedulePolicyMatrixTest.customizeSegment_overridesOpenAction` |
| R-16 | noScheduleMatch limit override | JVM `SchedulePolicyMatrixTest` |
| R-17 | noScheduleMatch session override | JVM `SchedulePolicyMatrixTest` |
| R-18 | customize segment limit action | `RulesCrossEnforcementTest.r18_customizeSegment_limitAction` |
| R-19 | open gate none skips friction | JVM `OnOpenSkipMatrixTest` / `RulesMatrixTest.evaluateOpenGate_returnsOpenDeterrentWhenConfigured` |
| R-20 | session notify only allowed flag | JVM `RulesMatrixTest.evaluateSessionCap_respectsSessionAction` |

Full combinatorial matrix: `RulesMatrixTest` (JVM) + `RulesInteractionTest` (JVM combined triples). Connected keepers are overlay chrome only (`r11`/`r13`/`r14`/`r18`/`rInt02`).

### R-INT — Rule interaction (open / session / period orthogonality)

| ID | Scenario | Automated test | Status |
|----|----------|----------------|--------|
| R-INT-01 | Open wait + daily hardBlock skips open wait | JVM `RulesInteractionTest.openWait_plus_dailyHardBlock_suppressesOpen` | pass |
| R-INT-02 | Open wait before daily extension block | `RulesCrossEnforcementTest.rInt02_openWait_dailyExtensions_openFirst` | pass |
| R-INT-03 | Open wait under limits then proceed | JVM `RulesInteractionTest.openWait_plus_dailyNotifyOnly_showsOpenFirst` | pass |
| R-INT-04 | noLimitToday bypasses session hardBlock for today | JVM `RulesInteractionTest.noLimitToday_bypassesSessionHardBlockForToday` | pass |
| R-INT-06 | noLimitToday bypasses period only | JVM `RulesInteractionTest.noLimitToday_bypassesWeeklyLimitForToday` | pass |

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
| E-06 | Leftover wall-clock grace pause does not allow; period extras do | `ExtensionEnforcementTest.e06_leftoverGracePause_doesNotAllow_extrasDo` |
| RF-01 | Session wait/math grant is session refill, not extension minutes | `ExtensionEnforcementTest.sessionWait_grantSessionRefill_allowsOverCap` |
| RF-02 | Period wait/math/break grant is period continue | `ExtensionEnforcementTest.periodWait_grantPeriodContinue_allowsOverCap` |
| E-07 | Surface overlay only | `ExtensionEnforcementTest.e07_surfaceOverlayOnly` |
| E-08 | Custom minutes extension | `ExtensionEnforcementTest.e08_customMinutes_extension` |
| E-09 | Extension after hard block denied | `ExtensionEnforcementTest.e09_afterHardBlock_denied` |
| E-10 | Overlay +5m over daily cap dismisses overlay, no re-block loop | `ExtensionGraceEnforcementTest.e10_overlayExtend5_overDailyCap_dismissesOverlayAndStaysAllowed` |
| E-11 | Over-cap usage + grace evaluates allowed | JVM `ExtensionGrantEngineTest` / `RulesInteractionTest.extensionGracePause_allowsDailyUsageWhenOverCap` |
| E-12 | Grace over daily cap: HUD shows used / base+bonus (not ∞ or 289h-style) | JVM `UsageDisplayLimitsTest` |
| E-13 | Overlay no-limit-today dismisses overlay | `ExtensionGraceEnforcementTest.e13_overlayNoLimitToday_dismissesOverlayAndStaysAllowed` |
| E-14 | Overlay no-limit-today uses session policy when limit policy disables | `CurrentUsageActionsTest.cu11_*` (in-app); overlay chrome is `e13_*` |
| E-15 | Open-wait after a remaining extension does not ask for another extension | `ExtensionGraceEnforcementTest.e15_openWait_afterExistingGrace_doesNotAskForAnotherExtension` |

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
| CU-10 | No limit today shared pool shows ∞ in Current Usage | `CurrentUsageActionsTest.cu04_*` + `cu09_*`; JVM `PauseManagerNoLimitTodayTest` |
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
| SET-04 | About is last in Settings; enforcement log range chips copy | `EnforcementSettingsTest.set04_aboutIsLast_logRangeChipsVisible` |
| SET-05 | Session continuity grace defaults to one minute and is visible | `EnforcementSettingsTest.sessionContinuityGrace_defaultsToOneMinuteAndIsVisible` |

---

## ST — Stats smoke

| ID | Scenario | Automated test |
|----|----------|----------------|
| ST-01 | Stats screen loads | `StatsScreenSmokeTest.st01_statsScreen_loads` |
| ST-02 | Period swipe changes range | `StatsScreenSmokeTest.st01_*` / `st03_*` load smoke |
| ST-03 | Bar chart with seeded usage | `StatsScreenSmokeTest.st03_barChart_seededUsage` |

---

## SYS — System integration

| ID | Scenario | Automated test |
|----|----------|----------------|
| SYS-01 | Launcher target home recents chain | `SystemIntegrationTest.sys01_launcherTargetHomeRecents` |
| SYS-02 | Settings foreground no crash | `SystemIntegrationTest.sys02_settingsForeground_noCrash` |
| SYS-03 | Split screen optional | Manual (`ManualScenariosTest`); not in the connected ship bar |

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
- Rules matrix combinatorics run in `RulesMatrixTest` / `SchedulePolicyMatrixTest` / `RulesInteractionTest` on JVM.
- Overlay leftover debugger is `ExtensionEnforcementTest` + `ExtensionGraceEnforcementTest` together. Ship bar is JVM then `:core-data:connectedDebugAndroidTest` then reduced `:app:connectedDebugAndroidTest` once. See [instrumented-testing.md](instrumented-testing.md).
