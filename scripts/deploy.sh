#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

ADB_DEFAULT="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
if ! command -v adb &>/dev/null; then
    if [[ -x "$ADB_DEFAULT" ]]; then
        export PATH="$(dirname "$ADB_DEFAULT"):$PATH"
    else
        echo "ERROR: adb not found. Add Android SDK platform-tools to PATH." >&2
        exit 1
    fi
fi

if ! adb devices | grep -q "device$"; then
    echo "ERROR: No device connected via adb." >&2
    exit 1
fi

echo "Building..."
./gradlew :app:assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"

if [[ ! -f "$APK" ]]; then
    echo "ERROR: APK not found at $APK" >&2
    exit 1
fi

echo "Installing..."
adb install -r "$APK"

echo "Launching..."
adb shell am start -n "com.frybynite.podcastapp/.MainActivity"

echo "Done."
