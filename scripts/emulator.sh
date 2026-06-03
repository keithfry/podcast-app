#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

EMULATOR="$HOME/Library/Android/sdk/emulator/emulator"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
AVD="${1:-}"

if ! command -v adb &>/dev/null; then
    if [[ -x "$ADB" ]]; then
        export PATH="$(dirname "$ADB"):$PATH"
    else
        echo "ERROR: adb not found. Add Android SDK platform-tools to PATH." >&2
        exit 1
    fi
fi

if [[ ! -x "$EMULATOR" ]]; then
    echo "ERROR: emulator not found at $EMULATOR" >&2
    exit 1
fi

# Pick AVD: arg > first available
if [[ -z "$AVD" ]]; then
    AVD=$("$EMULATOR" -list-avds 2>/dev/null | head -1)
fi

if [[ -z "$AVD" ]]; then
    echo "ERROR: No AVDs found. Create one in Android Studio → Device Manager." >&2
    exit 1
fi

# Check if emulator already running
EMULATOR_SERIAL=$(adb devices | awk '/emulator-/{print $1}' | head -1)

if [[ -z "$EMULATOR_SERIAL" ]]; then
    echo "Starting emulator: $AVD..."
    "$EMULATOR" -avd "$AVD" -no-snapshot-save &
    echo "Waiting for emulator to boot..."
    adb wait-for-device
    # Wait for boot to complete
    until adb shell getprop sys.boot_completed 2>/dev/null | grep -q "^1$"; do
        sleep 2
    done
    echo "Emulator ready."
    EMULATOR_SERIAL=$(adb devices | awk '/emulator-/{print $1}' | head -1)
else
    echo "Emulator already running: $EMULATOR_SERIAL"
fi

echo "Building..."
./gradlew :app:assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"

if [[ ! -f "$APK" ]]; then
    echo "ERROR: APK not found at $APK" >&2
    exit 1
fi

echo "Installing on $EMULATOR_SERIAL..."
adb -s "$EMULATOR_SERIAL" install -r "$APK"

echo "Launching..."
adb -s "$EMULATOR_SERIAL" shell am start -n "com.podcastapp/.MainActivity"

echo "Done."
