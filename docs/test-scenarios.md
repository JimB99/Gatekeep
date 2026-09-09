# Gatekeep test scenarios

Regression catalog for automated and manual testing. Each scenario ID maps to one or more tests.

## Running tests

```bash
# JVM unit tests (fast)
./gradlew :core-domain:test :app:testDebugUnitTest

# Emulator instrumented tests (start AVD first)
./gradlew :app:connectedDebugAndroidTest

# Single class
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.gatekeep.app.lock.MainActivityAppLockTest
```

**Prerequisites:** Android emulator running (`Medium_Phone_API_36.0` or similar), `local.properties` with `sdk.dir`, debug keystore at `keystore/debug.keystore`.

Gradle uses JDK 21 via `org.gradle.java.home` in `gradle.properties` (Studio JBR).

---

## Phase 1: App PIN lock

| ID | Scenario | Automated test |
|----|----------|----------------|
| PIN-01 | Cold start with lock enabled | `MainActivityAppLockTest.pin01_coldStart_showsLockScreen` |
| PIN-02 | Correct PIN unlocks to dashboard | `AppLockScreenTest.pin02_*`, `MainActivityAppLockTest.pin02_*` |
| PIN-03 | Wrong PIN stays locked | `AppLockScreenTest.pin03_wrongPin_staysLocked` |
| PIN-04 | Background (ON_STOP) requires PIN again | `MainActivityAppLockTest.pin04_backgroundingRequiresPinAgain`, `AppLockControllerTest.pin04_*` |
| PIN-05 | Screen lock requires PIN on wake | Manual / future (UiAutomator sleep key) |
| PIN-06 | Force-stop requires PIN on relaunch | Manual only (`am force-stop` kills in-process instrumentation) |
| PIN-07 | Rotation does not re-lock | `MainActivityAppLockTest.pin07_rotation_staysUnlocked`, `AppLockControllerTest.pin07_*` |
| PIN-08 | Lock disabled skips PIN | `MainActivityAppLockTest.pin08_lockDisabled_skipsLockScreen` |
| PIN-09 | No PIN hash skips lock | `MainActivityAppLockTest.pin09_noPin_skipsLockScreen` |
| PIN-10 | Rapid background/resume always locks | `MainActivityAppLockTest.pin10_rapidBackgroundResume_requiresPin` |

### Implementation notes

- Lock logic lives in `AppLockController` (unit tested).
- `MainActivity` uses `sessionUnlocked` starting `false` when lock is required, fixing the race where DataStore defaults briefly skipped the PIN screen.
- Instrumented tests seed `SettingsRepository` then `recreate()` the activity.

---

## Future phases (not yet automated)

- **Overlay stability** (O-01…): block overlay on app switch
- **Open gate** (G-01…): wait-on-open, profile PIN
- **Notifications** (N-01…): session timer, block alerts
- **Extensions** (E-01…): overlay/in-app extension grants
