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

load_local_env

application_id="${NOMAD_APPLICATION_ID:-com.iloapps.nomaddashboard}${NOMAD_DEBUG_APPLICATION_ID_SUFFIX:-.dev}"
remote_directory="/sdcard/Android/data/$application_id/files/Pictures/review-screenshots"
local_directory="$REPO_ROOT/output/screenshots/android/phone"
screenshot_test_class="com.iloapps.nomaddashboard.ScreenshotReviewTest"

mkdir -p "$local_directory"

adb -s "$emulator_serial" shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
adb -s "$emulator_serial" shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
adb -s "$emulator_serial" shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true

echo "Running screenshot tests on emulator $emulator_serial"
ANDROID_SERIAL="$emulator_serial" run_gradle \
  :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.captureScreenshots=true \
  -Pandroid.testInstrumentationRunnerArguments.class="$screenshot_test_class"

for filename in \
  dashboard-phone.png \
  settings-phone.png \
  visited-phone.png \
  timetracking-phone.png \
  about-phone.png
do
  adb -s "$emulator_serial" pull "$remote_directory/$filename" "$local_directory/$filename" >/dev/null
done

echo "Android review screenshots available at:"
echo "$local_directory"
