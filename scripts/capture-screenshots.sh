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

remote_directory="/sdcard/Download/nomad-dashboard-review-screenshots"
local_directory="$REPO_ROOT/output/screenshots/android/phone"
screenshot_test_class="com.iloapps.nomaddashboard.ScreenshotReviewTest"
screen_filter="${SCREEN:-}"

mkdir -p "$local_directory"

adb -s "$emulator_serial" shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
adb -s "$emulator_serial" shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
adb -s "$emulator_serial" shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true
adb -s "$emulator_serial" shell "rm -rf $remote_directory && mkdir -p $remote_directory" >/dev/null

if [[ -n "$screen_filter" ]]; then
  case "$screen_filter" in
    dashboard) screenshot_test_class="${screenshot_test_class}#capture_dashboard_phone" ;;
    settings) screenshot_test_class="${screenshot_test_class}#capture_settings_phone" ;;
    visited) screenshot_test_class="${screenshot_test_class}#capture_visited_phone" ;;
    timetracking) screenshot_test_class="${screenshot_test_class}#capture_timetracking_phone" ;;
    about) screenshot_test_class="${screenshot_test_class}#capture_about_phone" ;;
    *) fail "Unsupported SCREEN value '$screen_filter'. Use one of: dashboard, settings, visited, timetracking, about." ;;
  esac
fi

adb -s "$emulator_serial" logcat -c >/dev/null 2>&1 || true
adb -s "$emulator_serial" logcat -T 1 -s 'ScreenshotReview:I' '*:S' &
logcat_pid=$!
cleanup() {
  kill "$logcat_pid" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "Running screenshot tests on emulator $emulator_serial"
if [[ -n "$screen_filter" ]]; then
  echo "Screenshot selection: $screen_filter"
fi
ANDROID_SERIAL="$emulator_serial" run_gradle \
  :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class="$screenshot_test_class"

for filename in \
  dashboard-phone.png \
  settings-phone.png \
  visited-phone.png \
  timetracking-phone.png \
  about-phone.png
do
  if [[ -n "$screen_filter" ]]; then
    [[ "$filename" == "${screen_filter}-phone.png" ]] || continue
  fi
  adb -s "$emulator_serial" pull "$remote_directory/$filename" "$local_directory/$filename" >/dev/null
  [[ -s "$local_directory/$filename" ]] || fail "Expected screenshot export for $filename, but the local file is empty."
  echo "Exported $filename"
done

echo "Android review screenshots available at:"
echo "$local_directory"
