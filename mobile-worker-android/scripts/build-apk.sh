#!/usr/bin/env bash
set -euo pipefail

BUILD_TYPE="${1:-debug}"
case "$BUILD_TYPE" in
  debug)
    ASSEMBLE_TASK=assembleDebug
    LINT_TASK=lintDebug
    APK_PATH=app/build/outputs/apk/debug/app-debug.apk
    ;;
  release)
    ASSEMBLE_TASK=assembleRelease
    LINT_TASK=lintRelease
    APK_PATH=app/build/outputs/apk/release/app-release.apk
    ;;
  *)
    echo "Usage: $0 [debug|release]" >&2
    exit 2
    ;;
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
  [[ -f "$ANDROID_KEYSTORE_PATH" ]] || {
    echo "Keystore not found: $ANDROID_KEYSTORE_PATH" >&2
    exit 1
  }
fi

gradle --no-daemon :app:testDebugUnitTest ":app:${LINT_TASK}" ":app:${ASSEMBLE_TASK}"

[[ -f "$APK_PATH" ]] || {
  echo "Expected APK was not generated: $APK_PATH" >&2
  exit 1
}

if [[ "$BUILD_TYPE" == "release" ]]; then
  APKSIGNER=""
  if command -v apksigner >/dev/null 2>&1; then
    APKSIGNER="$(command -v apksigner)"
  else
    for sdk_root in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"; do
      candidate="$sdk_root/build-tools/36.0.0/apksigner"
      if [[ -n "$sdk_root" && -x "$candidate" ]]; then
        APKSIGNER="$candidate"
        break
      fi
    done
  fi

  [[ -n "$APKSIGNER" ]] || {
    echo "apksigner from Android Build Tools 36.0.0 is required to verify release APKs." >&2
    exit 1
  }
  "$APKSIGNER" verify --verbose "$APK_PATH"
fi

printf '%s\n' "$APK_PATH"
