#!/usr/bin/env bash
set -euo pipefail

DHU="$HOME/Library/Android/sdk/extras/google/auto/desktop-head-unit"

ADB_DEFAULT="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
if ! command -v adb &>/dev/null; then
    if [[ -x "$ADB_DEFAULT" ]]; then
        export PATH="$(dirname "$ADB_DEFAULT"):$PATH"
    else
        echo "ERROR: adb not found. Add Android SDK platform-tools to PATH." >&2
        exit 1
    fi
fi

if [[ ! -x "$DHU" ]]; then
    echo "ERROR: DHU not found at $DHU" >&2
    echo "Install via Android Studio → SDK Manager → SDK Tools → Android Auto Desktop Head Unit Emulator" >&2
    exit 1
fi

if pgrep -f "desktop-head-unit" &>/dev/null; then
    echo "DHU already running."
    exit 0
fi

if ! adb devices | grep -q "device$"; then
    echo "ERROR: No device connected via adb." >&2
    exit 1
fi

echo "Forwarding tcp:5277..."
adb forward tcp:5277 tcp:5277

echo "Starting DHU..."
"$DHU" &

echo "DHU started (PID $!)."
