#!/usr/bin/env bash
# Release AAB for Play Store, named gatekeep-<version>.aab from app/build.gradle.kts versionName.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

TOOLS="$(cd "$ROOT/.." && pwd)/.tools"
export JAVA_HOME="${JAVA_HOME:-$TOOLS/jdk-17.0.14+7}"
export ANDROID_HOME="${ANDROID_HOME:-$TOOLS/android-sdk}"

VERSION_NAME="$(grep 'versionName' "$ROOT/app/build.gradle.kts" | head -1 | sed -E 's/.*versionName = "([^"]+)".*/\1/')"
if [[ -z "$VERSION_NAME" ]]; then
  echo "Could not read versionName from app/build.gradle.kts" >&2
  exit 1
fi

./gradlew -Dorg.gradle.java.home="$JAVA_HOME" :core-domain:test :app:bundleRelease

SRC="$ROOT/app/build/outputs/bundle/release/app-release.aab"
DEST_DIR="$ROOT/dist"
DEST="$DEST_DIR/gatekeep-${VERSION_NAME}.aab"

if [[ ! -f "$SRC" ]]; then
  echo "Release AAB not found at $SRC" >&2
  exit 1
fi

mkdir -p "$DEST_DIR"
cp -f "$SRC" "$DEST"
echo "Gatekeep AAB: $DEST"