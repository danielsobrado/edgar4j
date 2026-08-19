#!/usr/bin/env bash
set -euo pipefail

BUILD_TYPE="${1:-debug}"
case "$BUILD_TYPE" in
  debug) TASK=assembleDebug ;;
  release) TASK=assembleRelease ;;
  *) echo "Usage: $0 [debug|release]" >&2; exit 2 ;;
esac

if ! command -v java >/dev/null 2>&1; then
  echo "JDK 17+ is required." >&2
  exit 1
fi

if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle 9.1.0 is required. Install it or use the GitHub Actions APK workflow." >&2
  exit 1
fi

if [[ "$BUILD_TYPE" == "release" ]]; then
  : "${ANDROID_KEYSTORE_PATH:?ANDROID_KEYSTORE_PATH is required for a signed release APK}"
  : "${ANDROID_KEYSTORE_PASSWORD:?ANDROID_KEYSTORE_PASSWORD is required}"
  : "${ANDROID_KEY_ALIAS:?ANDROID_KEY_ALIAS is required}"
  : "${ANDROID_KEY_PASSWORD:?ANDROID_KEY_PASSWORD is required}"
  [[ -f "$ANDROID_KEYSTORE_PATH" ]] || { echo "Keystore not found: $ANDROID_KEYSTORE_PATH" >&2; exit 1; }
fi

gradle --no-daemon :app:testDebugUnitTest :app:lintDebug ":app:${TASK}"
find app/build/outputs/apk -type f -name '*.apk' -print
