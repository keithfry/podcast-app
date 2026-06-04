#!/usr/bin/env bash
# Usage: ./scripts/logcat.sh [deep]
#   default — all logs from com.frybynite.podcastapp
#   deep    — DeepDive tag only

set -euo pipefail

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"

if [[ "${1:-}" == "deep" ]]; then
    "$ADB" logcat -v time DeepDive:D *:S
else
    PID=$("$ADB" shell pidof com.frybynite.podcastapp 2>/dev/null || true)
    if [[ -z "$PID" ]]; then
        echo "App not running — showing all logs, restart app to filter by PID"
        "$ADB" logcat -v time | grep -i "frybynite\|podcastapp"
    else
        "$ADB" logcat -v time --pid="$PID"
    fi
fi
