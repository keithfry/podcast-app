#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

EMULATOR="$HOME/Library/Android/sdk/emulator/emulator"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
AVD="${1:-Television_720p}"

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

# Check if TV emulator already running
TV_SERIAL=$(adb devices | awk '/emulator-/{print $1}' | while read s; do
    name=$(adb -s "$s" emu avd name 2>/dev/null | head -1 | tr -d '\r')
    [[ "$name" == "$AVD" ]] && echo "$s"
done | head -1)

if [[ -z "$TV_SERIAL" ]]; then
    echo "Starting TV emulator: $AVD..."
    "$EMULATOR" -avd "$AVD" -no-snapshot-save &
    echo "Waiting for emulator to boot..."
    adb wait-for-device
    until adb shell getprop sys.boot_completed 2>/dev/null | grep -q "^1$"; do
        sleep 2
    done
    echo "Emulator ready."
    TV_SERIAL=$(adb devices | awk '/emulator-/{print $1}' | head -1)
else
    echo "TV emulator already running: $TV_SERIAL"
fi

echo "Building..."
./gradlew :app:assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK" ]]; then
    echo "ERROR: APK not found at $APK" >&2
    exit 1
fi

echo "Installing on $TV_SERIAL..."
adb -s "$TV_SERIAL" install -r "$APK"

echo "Launching..."
adb -s "$TV_SERIAL" shell am start -n "com.frybynite.podlore/.MainActivity"

echo "Done."
