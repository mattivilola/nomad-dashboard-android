#!/bin/zsh
set -euo pipefail

source "$(dirname "$0")/android-env.sh"

require_command adb

physical_device_serial="$(
  adb devices | awk '
    NR > 1 && $2 == "device" && $1 !~ /^emulator-/ { print $1 }
  ' | head -n 1
)"

[[ -n "$physical_device_serial" ]] || fail "No connected physical Android device found."

echo "Running tests on physical device $physical_device_serial"
ANDROID_SERIAL="$physical_device_serial" run_gradle testDebugUnitTest connectedDebugAndroidTest
