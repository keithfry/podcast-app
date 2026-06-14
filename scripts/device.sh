#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

WIRELESS=0
WIRELESS_TARGET="10.0.0.34:37387"
AUTOMOTIVE=0
AUTOMOTIVE_AVD="Automotive_1408p_landscape"
LOCAL_PHONE=0
LOCAL_PHONE_AVD="Medium_Phone_API_36.1"

for arg in "$@"; do
    case "$arg" in
        --wireless) WIRELESS=1 ;;
        --automotive) AUTOMOTIVE=1 ;;
        local-phone) LOCAL_PHONE=1 ;;
        *) echo "ERROR: Unknown argument: $arg" >&2; exit 1 ;;
    esac
done

SDK_ROOT="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

ADB_DEFAULT="$SDK_ROOT/platform-tools/adb"
if ! command -v adb &>/dev/null; then
    if [[ -x "$ADB_DEFAULT" ]]; then
        export PATH="$(dirname "$ADB_DEFAULT"):$PATH"
    else
        echo "ERROR: adb not found. Add Android SDK platform-tools to PATH." >&2
        exit 1
    fi
fi

EMULATOR_BIN="$SDK_ROOT/emulator/emulator"

find_phone_emulator() {
    adb devices | awk '/emulator.*device$/{print $1}' | while read -r s; do
        name=$(adb -s "$s" emu avd name 2>/dev/null | grep -v '^OK' | tr -d '\r' | xargs)
        if [[ "$name" == "$LOCAL_PHONE_AVD" ]]; then echo "$s"; fi
    done | head -1
}

if [[ "$LOCAL_PHONE" == "1" ]]; then
    DEVICE_SERIAL=$(find_phone_emulator)

    if [[ -z "$DEVICE_SERIAL" ]]; then
        if [[ ! -x "$EMULATOR_BIN" ]]; then
            echo "ERROR: Emulator not found at $EMULATOR_BIN" >&2
            exit 1
        fi
        echo "Starting $LOCAL_PHONE_AVD..."
        "$EMULATOR_BIN" -avd "$LOCAL_PHONE_AVD" -no-snapshot-save &
        echo "Waiting for emulator to boot..."
        for i in $(seq 1 60); do
            DEVICE_SERIAL=$(find_phone_emulator)
            [[ -n "$DEVICE_SERIAL" ]] && break
            sleep 2
        done
        if [[ -z "$DEVICE_SERIAL" ]]; then
            echo "ERROR: Emulator did not appear within 120s." >&2
            exit 1
        fi
        echo "Waiting for boot to complete..."
        adb -s "$DEVICE_SERIAL" wait-for-device shell 'while [[ "$(getprop sys.boot_completed)" != "1" ]]; do sleep 2; done'
    else
        echo "Phone emulator already running: $DEVICE_SERIAL"
    fi
elif [[ "$AUTOMOTIVE" == "1" ]]; then
    # Find running automotive emulator
    DEVICE_SERIAL=$(adb devices | awk '/emulator.*device$/{print $1}' | head -1)

    if [[ -z "$DEVICE_SERIAL" ]]; then
        if [[ ! -x "$EMULATOR_BIN" ]]; then
            echo "ERROR: Emulator not found at $EMULATOR_BIN" >&2
            exit 1
        fi
        echo "Starting $AUTOMOTIVE_AVD..."
        "$EMULATOR_BIN" -avd "$AUTOMOTIVE_AVD" -no-snapshot-load &
        echo "Waiting for emulator to boot..."
        # Wait for adb to see the device
        for i in $(seq 1 60); do
            DEVICE_SERIAL=$(adb devices | awk '/emulator.*device$/{print $1}' | head -1)
            [[ -n "$DEVICE_SERIAL" ]] && break
            sleep 2
        done
        if [[ -z "$DEVICE_SERIAL" ]]; then
            echo "ERROR: Emulator did not appear within 120s." >&2
            exit 1
        fi
        # Wait for boot to complete
        echo "Waiting for boot to complete..."
        adb -s "$DEVICE_SERIAL" wait-for-device shell 'while [[ "$(getprop sys.boot_completed)" != "1" ]]; do sleep 2; done'
    fi
elif [[ "$WIRELESS" == "1" ]]; then
    echo "Connecting wirelessly to $WIRELESS_TARGET..."
    adb connect "$WIRELESS_TARGET" >/dev/null
    DEVICE_SERIAL="$WIRELESS_TARGET"
else
    DEVICE_SERIAL=$(adb devices | awk '/device$/ && !/emulator/{print $1}' | head -1)
    if [[ -z "$DEVICE_SERIAL" ]]; then
        echo "ERROR: No physical device connected. Connect via USB and enable USB debugging." >&2
        exit 1
    fi
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
