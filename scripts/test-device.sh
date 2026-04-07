#!/bin/zsh
set -euo pipefail

source "$(dirname "$0")/android-env.sh"

require_command adb

physical_device_serial="$(first_physical_device_serial)"

[[ -n "$physical_device_serial" ]] || fail "No connected physical Android device found."

echo "Running tests on physical device $physical_device_serial"
ANDROID_SERIAL="$physical_device_serial" run_gradle testDebugUnitTest connectedDebugAndroidTest
