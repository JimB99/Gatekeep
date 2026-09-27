# Building Gatekeep

Requirements: **JDK 17**, **Android SDK 35** (`compileSdk` / `targetSdk` as in `app/build.gradle`).

## Standard toolchain

Set `JAVA_HOME` to JDK 17 and `ANDROID_HOME` to your Android SDK, then:

```bash
./gradlew :core-domain:test :core-data:test :app:testDebugUnitTest
bash scripts/build_apk.sh
```

APK output: **`dist/gatekeep-<version>.apk`** (arm64-v8a).

## Private workspace toolchain

On the author’s machine:

```bash
export JAVA_HOME="../.tools/jdk-17.0.14+7"
export ANDROID_HOME="../.tools/android-sdk"
```

## Signing

**Prerequisite:** `keystore/debug.keystore` must exist (not committed). Without it, release builds are unsigned.

Create once:

```bash
mkdir -p keystore
keytool -genkeypair -v -keystore keystore/debug.keystore -alias androiddebugkey \
  -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android \
  -dname "CN=Gatekeep Debug, OU=Dev, O=Gatekeep, L=Local, ST=Local, C=NL"
```
