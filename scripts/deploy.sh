#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if ! command -v adb &>/dev/null; then
    echo "ERROR: adb not found. Add Android SDK platform-tools to PATH." >&2
    exit 1
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
adb shell am start -n "com.podcastapp/.MainActivity"

echo "Done."
