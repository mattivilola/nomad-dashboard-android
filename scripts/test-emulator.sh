#!/bin/zsh
set -euo pipefail

source "$(dirname "$0")/android-env.sh"

require_command adb

"$REPO_ROOT/scripts/start-emulator.sh" "${1:-${NOMAD_ANDROID_AVD:-Pixel_5_API_31}}"

emulator_serial="$(
  adb devices | awk '
    /^emulator-[0-9]+\tdevice$/ { print $1 }
  ' | head -n 1
)"

[[ -n "$emulator_serial" ]] || fail "No booted emulator found after startup."

echo "Running tests on emulator $emulator_serial"
ANDROID_SERIAL="$emulator_serial" run_gradle testDebugUnitTest connectedDebugAndroidTest
