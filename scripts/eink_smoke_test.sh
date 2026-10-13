#!/bin/bash

# Smoke test of the Zotero E-Ink APK on a running emulator or device:
# installs the APK, launches the app, checks that it is still alive after a while
# and that no crash was logged, then saves a screenshot and the logcat.
#
# Usage: scripts/eink_smoke_test.sh <apk> <output dir> [wait seconds]

set -eo pipefail

APK="$1"
OUT_DIR="$2"
WAIT_SECONDS="${3:-60}"
PACKAGE="org.zotero.android.eink"
ACTIVITY="org.zotero.android.screens.root.RootActivity"

if [ -z "$APK" ] || [ -z "$OUT_DIR" ]; then
    echo "Usage: $0 <apk> <output dir> [wait seconds]"
    exit 2
fi

mkdir -p "$OUT_DIR"

adb wait-for-device
adb install -r "$APK"
adb logcat -c

adb shell am start -W -n "$PACKAGE/$ACTIVITY"
sleep "$WAIT_SECONDS"

adb exec-out screencap -p > "$OUT_DIR/screenshot.png"
adb logcat -d > "$OUT_DIR/logcat.txt"

STATUS=0
if ! adb shell pidof "$PACKAGE" > /dev/null; then
    echo "FAILURE: $PACKAGE is not running anymore."
    STATUS=1
fi
if grep -A30 "FATAL EXCEPTION" "$OUT_DIR/logcat.txt" | grep -q "$PACKAGE"; then
    echo "FAILURE: crash found in logcat:"
    grep -A30 "FATAL EXCEPTION" "$OUT_DIR/logcat.txt"
    STATUS=1
fi

if [ "$STATUS" -eq 0 ]; then
    echo "SUCCESS: $PACKAGE started and is still running after ${WAIT_SECONDS}s."
fi
exit "$STATUS"
