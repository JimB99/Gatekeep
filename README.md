# Gatekeep

An Android app that helps you manage screen time with profiles, session limits, schedules, friction unlocks, and a Session HUD timer bar.

## Features

- Select and categorize apps
- Daily, weekly, hourly, and per-session limits with mandatory breaks
- Multiple profiles with optional PIN lock
- Schedule windows (multiple per day)
- Pause 5/15/60 min, focus mode, emergency bypass
- Math / phrase / hold-button / password friction to continue
- Session HUD overlay (Digital Wellbeing-style bottom timer bar)
- Statistics with streaks and charts
- Home screen widget
- Backup/restore profiles (JSON)
- Strict mode and optional device admin deterrent
- Gradual limit tightening

## Build

Requirements: JDK 17, Android SDK 35

```bash
export JAVA_HOME="c:/Users/JimBuisman/Desktop/Private/.tools/jdk-17.0.14+7"
export ANDROID_HOME="c:/Users/JimBuisman/Desktop/Private/.tools/android-sdk"
./gradlew :core-domain:test :core-data:test :app:testDebugUnitTest :app:assembleRelease
```

APK output: `app/build/outputs/apk/release/app-release.apk` (arm64-v8a only).

**Prerequisite:** `keystore/debug.keystore` must exist. Without it, release builds are unsigned.

For local signing, create a shared keystore once (not committed):

```bash
mkdir -p keystore
keytool -genkeypair -v -keystore keystore/debug.keystore -alias androiddebugkey \
  -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android \
  -dname "CN=Gatekeep Debug, OU=Dev, O=Gatekeep, L=Local, ST=Local, C=NL"
```

## Permissions

See [docs/PERMISSIONS.md](docs/PERMISSIONS.md).

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
