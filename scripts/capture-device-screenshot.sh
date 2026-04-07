#!/bin/zsh
set -euo pipefail

source "$(dirname "$0")/android-env.sh"

export_android_env
require_command adb

physical_device_only="${PHYSICAL_DEVICE_ONLY:-0}"

if [[ "$physical_device_only" == "1" ]]; then
  target_serial="$(require_physical_adb_target_serial)"
else
  target_serial="$(require_adb_target_serial)"
fi

timestamp="$(date '+%Y%m%d-%H%M%S')"
safe_serial="${target_serial//[^[:alnum:]._-]/_}"
output_dir="$REPO_ROOT/output/screenshots/device"
output_path="$output_dir/${timestamp}-${safe_serial}.png"
remote_path="/sdcard/Download/nomad-dashboard-debug-screenshot.png"

mkdir -p "$output_dir"

if [[ "$target_serial" == emulator-* ]]; then
  echo "Capturing screenshot from emulator $target_serial"
else
  echo "Capturing screenshot from physical device $target_serial"
fi

adb -s "$target_serial" shell screencap -p "$remote_path"
adb -s "$target_serial" pull "$remote_path" "$output_path" >/dev/null
adb -s "$target_serial" shell rm -f "$remote_path" >/dev/null 2>&1 || true

echo "Saved screenshot to $output_path"
