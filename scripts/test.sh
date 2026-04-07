#!/bin/zsh
set -euo pipefail

source "$(dirname "$0")/android-env.sh"

connected_devices() {
  export_android_env
  adb devices | awk 'NR > 1 && $2 == "device" { print $1 }'
}

run_gradle testDebugUnitTest

if [[ -n "$(connected_devices)" ]]; then
  run_gradle connectedDebugAndroidTest
else
  echo "No connected Android device or emulator detected; skipping connectedDebugAndroidTest."
fi
