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

SDK_ROOT="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
EMULATOR_BIN="$SDK_ROOT/emulator/emulator"

if [[ ! -x "$EMULATOR_BIN" ]]; then
    echo "ERROR: emulator not found at $EMULATOR_BIN" >&2
    exit 1
fi

AUTOMOTIVE_AVD="Automotive_1408p_landscape"

# Check if an emulator is already running
DEVICE_SERIAL=$(adb devices | awk '/emulator.*device$/{print $1}' | head -1)

if [[ -z "$DEVICE_SERIAL" ]]; then
    echo "Starting emulator: $AUTOMOTIVE_AVD"
    "$EMULATOR_BIN" -avd "$AUTOMOTIVE_AVD" -no-snapshot-load &

    # Poll for emulator to appear in adb devices
    echo "Waiting for emulator to boot..."
    START_TIME=$(date +%s)
    while true; do
        DEVICE_SERIAL=$(adb devices | awk '/emulator.*device$/{print $1}' | head -1)
        if [[ -n "$DEVICE_SERIAL" ]]; then
            break
        fi

        ELAPSED=$(($(date +%s) - START_TIME))
        if [[ $ELAPSED -gt 120 ]]; then
            echo "ERROR: Emulator failed to boot within 120 seconds." >&2
            exit 1
        fi

        sleep 2
    done
fi

# Wait for the emulator to fully boot
echo "Waiting for device to fully boot..."
adb -s "$DEVICE_SERIAL" wait-for-device shell 'while [[ "$(getprop sys.boot_completed)" != "1" ]]; do sleep 2; done'

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
