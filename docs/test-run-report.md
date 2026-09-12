# Gatekeep test run report (2026-09-12)

## Production changes shipped

| Area | Change |
|------|--------|
| **Accessibility config** | Lighter service flags (`canRetrieveWindowContent=false`, longer `notificationTimeout`) to reduce OS revocation risk |
| **Revoked a11y UX** | `accessibilityOptedIn` setting, health check on refresh, banner + notification when OS disables service |
| **Enforcement fallback** | `ensureForegroundMonitoring()` polling without requiring live a11y service |
| **Room Flow parity** | `getMonitoredApps()`, `getActiveProfiles()`, `getActivePauses()` suspend reads in coordinator evaluate path (fixes empty first-emission) |
| **Usage stats guard** | `UsageStatsCollector.queryEvents()` returns empty when permission denied (onboarding ob02) |

## JVM test results

| Suite | Status |
|-------|--------|
| `:core-domain:test` | **PASS** |
| `:core-data:test` | **PASS** |
| `:app:testDebugUnitTest` | **PASS** |

## Emulator — `@LargeTest` enforcement suites

| Suite | Result |
|-------|--------|
| `RulesCrossEnforcementTest` | **25/25 PASS** |
| `OpenGateTest` | **15/15 PASS** |
| `OverlayStabilityTest` | **12/12 PASS** |
| `TimeLimitEnforcementTest` | **11/11 PASS** |
| `ScheduleEnforcementTest` | **10/10 PASS** |
| `ExtensionEnforcementTest` | **8/8 PASS** |
| `NotificationEnforcementTest` | **12/12 PASS** |
| `MultiProfileEnforcementTest` | **3/3 PASS** |
| `PollingConsistencyTest` | **7/7 PASS** |
| `PauseEnforcementTest` | **9/9 PASS** |

## Fast instrumented (exclude `@LargeTest`)

| Status | Notes |
|--------|-------|
| **60/60 PASS** (5 skipped) | Includes onboarding, app lock, settings, profiles, permissions smoke tests |

## Test harness improvements

| Issue | Fix |
|-------|-----|
| A11y disabled after emulator crash | Polling-first setup; no shell a11y re-enable; manual `onTargetLaunched` / `onNavigatedAway` hooks |
| FGS crash in tests | Stopped calling `onAccessibilityConnected()` in unhealthy-a11y path (was setting `accessibilityOptedIn` → FGS start) |
| Stale overlay in UI assertions | `awaitBlockOverlayForTests()`, `awaitAllowedWithoutOverlay()` with stale overlay clear |
| `StaleObjectException` in overlay text reads | Retry in `overlayMessageText()` |
| Cross-test settings pollution | `seedEnforcementReady()` resets `focusModeUntilMs`, `accessibilityOptedIn` |
| Non-deterministic pause suite order | `@FixMethodOrder(NAME_ASCENDING)` on `PauseEnforcementTest` |

## Triage log (this session)

| Scenario | Root cause | Fix |
|----------|------------|-----|
| `rInt03` | UI friction-gone assertion flaky after open wait | `assertAllowedWithoutBlockingOverlay()` |
| `g04` | No assertion; home didn't notify coordinator | `onNavigatedAway` hook + `assertOverlayHidden()` |
| `o02`/`pa02` TARGET_B overlay | Slow overlay attach on Settings | `assertBlockedWithOverlay()` polling |
| `o04` | `StaleObjectException` on break countdown text | Retry + `assertOverlayStable()` |
| `pa05` | `observeActivePauses().first()` empty emission; focus mode leaked between tests | `getActivePauses()` + settings reset + fixed test order |
| `ob02` | `SecurityException` on usage query after permission revoke | Guard in `UsageStatsCollector` |

## Release recommendation

**Green for continued development** — JVM suite and all exercised `@LargeTest` enforcement suites pass on `Medium_Phone_API_36.0`. Re-run per-class batches after emulator crashes (`./gradlew --stop` between classes). Monitor a11y health on physical devices after the lighter service config change.
