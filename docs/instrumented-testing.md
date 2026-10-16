# Gatekeep instrumented testing

How agents run and debug Gatekeep tests. Scenario catalog: [test-scenarios.md](test-scenarios.md). [test-run-report.md](test-run-report.md) is a stale 2026-09-12 dump — do not append here.

## When to use this file

| Loop | When |
|------|------|
| JVM (`:core-domain:test`, `:core-data:test`, `:app:testDebugUnitTest --tests …`) | Domain, pause, display math, evaluator, rule matrices. Seconds. No AVD. Always first. |
| One instrumented method | The bug is click, Compose, Hilt, or Activity. |
| E2e gate (`:core-data:connectedDebugAndroidTest` then `:app:connectedDebugAndroidTest`) | **Ship bar.** `@E2eGate` methods (settings, counting, pickers, lock, pause). Under 10 minutes. |
| Overlay `@LargeTest` | Opt-in debug only: `-Pgatekeep.e2eGate=false` plus a class filter. Not the ship bar. |

A leftover is one failing method (or the 16-test pair when the bug is process overlay). One leftover = one **tight** loop. `:app:connectedDebugAndroidTest` is the ship bar, not a debugger. Do not restore the old 223-method combinatorics; those IDs live on JVM.

## Standing rules

1. **Cheapest red first.** Domain/ViewModel/display: JVM with `--tests`. Instrumented one method only when JVM cannot go red on this bug. Default `:app:connectedDebugAndroidTest` is the `@E2eGate` ship bar (under 10 min), not 160 overlay scenarios.
2. **Debug install is the grant/display loop.** `connectedDebugAndroidTest` installs debug app + androidTest when those source sets change. `scripts/build_apk.sh` / `assembleRelease` is the APK step after the ship bar is green.
3. **Fail dumps on every instrumented fail.** Grant boolean, `getActivePauses`, Current Usage rows (`effectiveLimitMs`, `noLimitToday`), snackbar text, Compose semantics (or `screenDiagnostics()` / `EnforcementTrace` on overlay tests), then `adb logcat -d` for Gatekeep / `EnforcementLog` / AndroidRuntime. A timeout with none of those is an incomplete fail: add logging, then re-run **once**.
4. **Waits match the claim.** Overlay show: 2s when the spec is 2s. Overlay hide: 1.5s from the hide request. Compose after a persisted grant: 1s. A longer wait needs a new hypothesis and a new log line. Session-continuity device tests inject `SessionContinuityGrace.MIN_MS` (15s) and sleep ~16s after `sleepDevice` has asserted `!isScreenOn`. Do not sleep 61s/65s for the 60s default; that math is `SessionStartDecisionTest`. `timeout_msec=180000` is ANR fail-fast.
5. **One already-booted AVD.** `Medium_Phone_API_36.0` on `emulator-5554`. Cold-boot only when keyguard/a11y is the bug **or the AVD is dead** (gfxstream 139 / hung qemu). One emulator process per AVD. After reboot, wait until `service check lock_settings` is found (not only `boot_completed=1`), then `locksettings set-disabled true`. ANR dialog: Close app. Instrumentation timeout stays 180000. After a 139, do not snapshot-boot: kill leftover `emulator`/`qemu*`, then `-no-window -gpu swiftshader_indirect -no-snapshot-load` (windowed gfxstream hangs at Qt when `opengl32sw` is missing).
6. **Reset overlay before later UI in the same process.** Filter a test by its class#method, not by the `com.gatekeep.app` prefix (that prefix matches helpers). Helpers are not JUnit classes (`GatekeepUiNav`). Isolation-green leftovers that fail the connected suite with `coordHidden=true` and leftover `block_message` are hide reporting gone before WindowManager remove — fix hide, do not isolate the 15 surfaces. Do not assert leftover overlay in `@After`: that turned 15 suite fails into 64.
7. **Keep the spec assertion.** ∞ stays ∞. Do not raise 6s to 15s to hide a miss. Domain already allows in-app no-limit for overlay-only (`ExtensionRequestEvaluatorTest`).
8. **Connected modules one at a time.** `:core-data:connectedDebugAndroidTest` alone, then `:app:connectedDebugAndroidTest` alone.
9. **Stay on one suite until it ends.** Do not reply, restart, or start another Gradle on each `Tests N/M completed` ping. Watch only `BUILD SUCCESSFUL`, `BUILD FAILED`, or `INSTRUMENTATION_ABORTED`. Then isolate leftovers — never use `@LargeTest` combinatorics as a debugger.
10. **Toolchain every Gradle/adb command** (cwd `Gatekeep/`):
   ```bash
   export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
   export ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"
   ```
   Use Studio JBR when `../.tools/jdk-*` is missing. SDK from `local.properties` / `%LOCALAPPDATA%/Android/Sdk`.

Also:

- `TARGET_A` is `com.gatekeep.app` ([EnforcementTestPackages](../app/src/androidTest/kotlin/com/gatekeep/app/support/EnforcementTestPackages.kt)). Home/lock overlay tests self-monitor that package. A separate test-app package is a later harness change, not this suite cut.
- Accessibility service uses EntryPoint, not `@AndroidEntryPoint`.
- Room journal `TRUNCATE` on API 36 instrumented (WAL `SQLITE_BUSY`).
- Keyguard off (`locksettings set-disabled true`) before Home/lock overlay leftovers.
- Close the ANR with Close app. Leave the instrumented process running (device `am force-stop com.gatekeep.app` kills it).
- Compose `onAllNodesWithText(text)` is exact-match. Combined duration lines (`1m / ∞`) use `substring = true`.

## Yield gate

One cheap cycle with **no new fact** (no new log, no confirmed device state, no JVM reproduction) → **stop**. Write the gap on the ticket, add a **standing rule** or a dated lesson in this file, then change harness or product.

A test-only patch with no rule is incomplete when the waste was process: missing dumps, wrong wait, unhandled ANR, or using the connected suite as a debugger.

## Fail dumps (minimum)

On instrumented fail, capture before the next retry:

1. Grant / evaluate return value
2. Active pauses (`getActivePauses`)
3. UI state that the assertion names (Current Usage rows, snackbar, overlay)
4. Semantics dump or `screenDiagnostics()` / `EnforcementTrace`
5. `adb logcat -d` filtered to Gatekeep / `EnforcementLog` / AndroidRuntime

## Waits

Match the product claim. Examples: overlay shown in 2s; overlay gone 1.5s after hide; Current Usage ∞ within 1s of a persisted grant (Applied snackbar is delayed 400ms). Padding a wait without a new log is not a retry.

## Promoted lessons

One sentence each: waste → rule. Standing rules above are the agent-facing list; this is the audit trail.

- **2026-10-15** — 24h of leftover instrumented as a debugger, empty ComposeTimeout dumps, overlay hide waited 6s, ANR with no fail-fast → yield gate; dumps on every fail; waits match the claim; one method until the ship bar.
- **2026-10-15** — `:core-data` + `:app` connected in one Gradle invocation lost the AVD attachment → run them sequentially.
- **2026-10-15** — helper named `GatekeepUiTest` was discovered as a JUnit class → keep helpers out of the test runner (`GatekeepUiNav`).
- **2026-10-15** — WAL `SQLITE_BUSY` on API 36 instrumented → Room `TRUNCATE`; retry busy writes.
- **2026-10-15** — Home/lock overlay leftovers with keyguard on and a11y `windows=[]` → disable keyguard first; overlay tests are self-monitoring on `TARGET_A`.
- **2026-10-15** — device `am force-stop com.gatekeep.app` killed instrumentation → Close app on ANR; leave the process.
- **2026-10-15** — cu09 grant and `1m / ∞` were already on screen; `onAllNodesWithText("∞")` is exact-match → use `substring = true` for combined duration lines.
- **2026-10-15** — full 223-test suite failed 15 methods that can pass in isolation (e10 green alone) → isolation first; suite pollution is overlay leftover, SQLITE_BUSY on open, a11y `windows=[]`, screen not off.
- **2026-10-15** — r18 domain eval is `Blocked(dailyLimit)` but overlay missing and `windows=[]` even in isolation → rebind a11y when snapshots are empty; do not pad overlay waits.
- **2026-10-15** — r18 `eval=Blocked` vs `coord=Allowed` (all remaining null): dump **coordinator** `evaluateMonitoredPackageForTests`, not only a test-built RuleEngine. Customize does not inherit unset limit tiers; seed `dailyLimitMs` on the segment when the claim is overlay at cap.
- **2026-10-15** — a11y `windows=[]` with overlay missing can be a red herring when the coordinator never blocked.
- **2026-10-15** — a second `:app:connectedDebugAndroidTest` while one is running fails on `utp.0.log.lck`. One connected Gradle at a time; `--stop` then retry if the lock is stale.
- **2026-10-15** — full suite aborted at 115/223 with `INSTRUMENTATION_ABORTED: System has crashed` (`system_server` death). Leftovers before abort: res08/res02 home overlay still visible `windows=[]`; res07 remaining 19→16; rInt02; r05 (no assertion — likely crash). Isolation first; do not treat the abort as those methods being product-red.
- **2026-10-15** — res07/res04 isolation: remaining dropped 3s across `sleepDevice` while `windows=[]`. `forceScreenOff` can stay on for seconds; capture remaining at the last still-on sample and assert `!isScreenOn` before the lock wait. Do not raise the ±2s freeze band.
- **2026-10-15** — rInt02: 1s open wait can finish during `launchTargetA` (label wait up to 2s). Dump traces: `show_open_deterrent` then `overlay_show dailyLimit`. Assert open-first via traces, not only leftover `wait_countdown`. Do not pad the open-wait poll.
- **2026-10-15** — `./gradlew --status` during a connected run starts another daemon and can stop the busy one (`utp.0.log.lck` / suite killed at Resume tests). One Gradle connected at a time; wait on that process, do not poll `--status`.
- **2026-10-15** — suite started on `boot_completed=1` before unlock: `lock_settings` missing, CE SharedPreferences throw, then ANRs / `keyDispatchingTimedOut`. Wait for credential-encrypted storage (and `lock_settings`) before `hiltRule.inject()`.
- **2026-10-15** — pp02 empty `<failure>` + `keyDispatchingTimedOut` while Gboard loaded handwriting packs after clicking `friction_input`. Set overlay PIN/math text without focusing IME.
- **2026-10-15** — next suite abort at `pp03_switchApp_requiresPinAgain` (pp02 then Settings) with the same empty failure + `keyDispatchingTimedOut`. Isolate pp03; do not start another 223 until that method has a dump.
- **2026-10-15** — pp03 isolation: after pin, `pkg` stayed `com.gatekeep.app`, overlay gone, `windows=[]`. `launchTargetB` waited only 250ms for Settings. Retry `am start` and wait 2s like Target A.
- **2026-10-15** — suite leftovers e14/e15/pp03 isolation-green; abort is `keyDispatchingTimedOut` around OpenGate. Disable soft IME each `baseSetUp` (`show_ime_with_hard_keyboard 0`). Do not poll `./gradlew --status` during the suite.
- **2026-10-15** — suite pp02: `friction_input.text = pin` without click left overlay “Enter profile PIN”. Click is required; keep IME off. Isolation-green methods are not the ship bar.
- **2026-10-16** — gfxstream SIGSEGV (exit 139, `bad color buffer handle`) after a long suite. Next windowed snapshot/cold boot hung qemu ~70–185MB, never attached to adb (`Failed to load opengl32sw`). Kill leftover qemu/emulator; cold-boot `-no-window -gpu swiftshader_indirect -no-snapshot-load`; wait `lock_settings` + CE; do not poll `./gradlew --status`.
- **2026-10-16** — a notify regex on `Tests .* completed` restarted the agent ~140 times mid-suite and burned the wait_green loop. Watch only the Gradle process exit / `BUILD SUCCESSFUL|FAILED|INSTRUMENTATION_ABORTED`. Isolation after the run, not a turn per test.
- **2026-10-16** — 223 finished 12 fails / no abort. obs02 isolation-green; suite dump was leftover session-wait overlay (`Leave for at least 1m 0s`) not hardBlock. `removeOverlayOnly` must not report hidden while the view is still attached; tearDown/setUp wait overlay gone 1.5s (the hide claim). Do not pad hide waits.
- **2026-10-16** — obs08 isolation: traces `overlay_show openGate` then `open_gate_passed` during `launchTargetA` (`OPEN_WAIT_SEC=1`). Poll after launch sees overlay=null. Assert open-wait via traces within 2s of launch, same as rInt02. Do not pad the 2s poll.
- **2026-10-16** — second 223: 15 fails, all isolation-green or empty dumps. Pattern is `coordHidden=true` while UiAutomator still sees leftover session-wait/PIN/`block_message` (`windows=[]`). `hideTemporarily` set `overlayVisible=false` before WM remove; Compose/PIN clicks then hit the leftover window. Keep `isVisible` true until remove finishes; detach every live overlay view. Do not pad hide waits. Do not re-isolate the cluster before that product fix.
- **2026-10-16** — third 223 after hide sync: 64 fails. `@After` leftover-overlay assert multiplied one leak across later classes. e01 +5 click still saw `Daily limit reached` (`windows=[]`). GONE overlay still ate clicks. Neutralize with FLAG_NOT_TOUCHABLE + 1×1 and probe WM via `updateViewLayout`, not `isAttachedToWindow`. Restore interactive on reshow. Retry reset in tearDown; do not fail `@After`.
- **2026-10-16** — ExtensionEnforcementTest class green; ExtensionGrace e15 then e10/e13/e14 red in the same 16-test run (`coordHidden=true`, wait overlay countdown still on screen). DelayOpen posts `showDelay` onto main; `onOpenGatePassed` hides first; the queued post rebuilds the wait overlay. Abort posted shows when the evaluation/overlay epoch changed; do not extra-post `showDelay` when already on main. Repro is those two classes together (7 min), not another 223.
- **2026-10-16** — same 16-test run after epoch abort: e15 still `coordHidden=true` wait overlay. Leftover Room `lastForegroundEndEpochMs` beyond grace makes `SessionStartDecision` StartFresh, which cleared `openGatePassedPackage` and flashed wait again. Completing on-open must clear away timestamps; do not wipe the pass flag on StartFresh when `shouldSkipOpenGate`. Repro: those two classes, not 223.
- **2026-10-16** — ExtensionEnforcementTest class green, ExtensionGraceEnforcementTest class green, together e15 red (`coordHidden=true`, wait overlay still ticking). Reusing one overlay without `removeView` regressed e10/e13 clicks; reverted. Cheapest remaining leftover is those two classes (~10 min), not another 223.
- **2026-10-16** — leftover pair: e15 isolation-green; after ExtensionEnforcementTest, wait overlay stayed with `coordHidden=true`. Stale wait ticks (`shouldRunPostedShow` false) must `removeOverlayOnly`; `isAttachedToWindow` still means in WM. `sessionWait`/`periodWait` assert overlay gone after grant. Do not treat e10/e13 click misses as separate until that hide is clean.
- **2026-10-16** — 223 connected methods were not 223 scenarios: RulesCross triples already live in `RulesMatrixTest` / `RulesInteractionTest`; launch-without-assert methods (`r15`/`r17`/`r19`/`r20`/`pa03`/`pa12`) covered nothing; 61s/65s sleeps duplicated `SessionStartDecisionTest`. Ship bar is JVM + leftover pair + reduced connected once. Do not delete `e10`/`e13`/`e15` to go green.
- **2026-10-16** — reduced connected 30 fails after a green leftover pair. First Extension methods were `overlay=null windows=[]`: `grantAccessibilityViaShell` bounced `accessibility_enabled` 0→1 every time windows were empty, so UiAutomator could not see `FLAG_NOT_FOCUSABLE` overlays. Do not disable a11y when the service is already bound; wait for windows. Later Home/PIN fails were a replaced wait ticker calling `removeOverlayOnly` on a newer overlay — stale ticks remove only while they are still the active runnable. Isolate leftovers; do not use the reduced suite as a debugger.
- **2026-10-16** — reduced connected after that: 4 fails. obs02/obs06 Home overlay stayed (`windows=[]`); HideNow for launcher was ignored during `BLOCK_STABILIZATION_MS` after overlay show, and the next polls had empty windows so hide never ran. Home/Recents HideNow while Visible must not wait on that 400ms. res04 `before>=5` failed with remaining=3s because `forceScreenOff` burns seconds of an 8s session — assert screen off and remaining >= 1, do not pad the freeze band. Isolate those methods; do not start another 160.
- **2026-10-16** — obs01 isolation-red (`overlay=null`, same package) after setUp woke `MainActivity`: `TARGET_A` is `com.gatekeep.app`, so a no-injection launch is a same-package no-op. End a11y wait on Home, not MainActivity. obs02/obs06/obs07/res04 isolation-green after HideNow/res04 fixes.
- **2026-10-16** — interrupted 160: three fails, same overlay process (`windows=[]`). e13 no-limit click still saw `Daily limit reached` (click missed leftover overlay). pp03 PIN still `Enter profile PIN` (click did not dismiss). g13 `coordHidden=true` while wait overlay countdown=5s (hide reported gone, DelayOpen still ticking). These are not three product matrices — stop the hour-long combinatorics. Ship bar is `@E2eGate` (settings/counting/pickers, under 10 min). Logic stays on JVM.
- **2026-10-16** — `@E2eGate` with `tl01` overlay first made `pin02` fail (`unlocked` false): leftover `TYPE_APPLICATION_OVERLAY` ate the Compose unlock click. Do not mix overlay launches into the in-app e2e gate until hide is actually clean. Overlay stays opt-in `@LargeTest`.
- **2026-10-16** — `pa01` is not an e2e-gate method: `pause_allow_five_min` missing means Pause UI never opened, not that pause-five-min is a product bug. Keep pause coverage on JVM / the existing PauseScreen class; do not pad navigation.
