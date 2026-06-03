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

DEVICE_SERIAL=$(adb devices | awk '/device$/ && !/emulator/{print $1}' | head -1)

if [[ -z "$DEVICE_SERIAL" ]]; then
    echo "ERROR: No physical device connected. Connect via USB and enable USB debugging." >&2
    exit 1
fi

echo "Device: $DEVICE_SERIAL ($(adb -s "$DEVICE_SERIAL" shell getprop ro.product.model 2>/dev/null | tr -d '\r'))"

echo "Building..."
./gradlew :app:assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"

if [[ ! -f "$APK" ]]; then
    echo "ERROR: APK not found at $APK" >&2
    exit 1
fi

echo "Installing..."
adb -s "$DEVICE_SERIAL" install -r "$APK"

echo "Launching..."
adb -s "$DEVICE_SERIAL" shell am start -n "com.frybynite.podcastapp/.MainActivity"

echo "Done."
