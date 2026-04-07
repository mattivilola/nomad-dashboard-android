#!/bin/zsh
set -euo pipefail

source "$(dirname "$0")/android-env.sh"

export_android_env

require_command adb
require_command emulator

default_avd="${NOMAD_ANDROID_AVD:-Pixel_5_API_31}"
avd_name="${1:-$default_avd}"
show_window="${NOMAD_ANDROID_EMULATOR_WINDOW:-0}"
default_sysdir="$ANDROID_SDK_ROOT/system-images/android-34/google_apis_playstore/arm64-v8a"
sysdir_override="${NOMAD_ANDROID_EMULATOR_SYSDIR:-$default_sysdir}"

available_avds="$(emulator -list-avds 2>/dev/null || true)"
if [[ -z "$available_avds" || "$available_avds" != *"$avd_name"* ]]; then
  fail "AVD '$avd_name' not found. Available AVDs: ${available_avds:-none}"
fi

existing_serial="$(
  adb devices | awk '
    /^emulator-[0-9]+\tdevice$/ { print $1 }
  ' | head -n 1
)"

if [[ -n "$existing_serial" ]]; then
  echo "Emulator already running on $existing_serial"
else
  adb kill-server >/dev/null 2>&1 || true
  adb start-server >/dev/null

  log_path="${TMPDIR:-/tmp}/nomad-dashboard-emulator.log"
  emulator_args=(
    -avd "$avd_name"
    -netdelay none
    -netspeed full
    -no-snapshot-save
    -no-boot-anim
    -gpu swiftshader_indirect
  )

  if [[ "$show_window" != "1" ]]; then
    emulator_args+=(-no-window)
  fi

  if [[ -d "$sysdir_override" ]]; then
    emulator_args+=(-sysdir "$sysdir_override")
  fi

  nohup emulator "${emulator_args[@]}" >"$log_path" 2>&1 &
  echo "Starting emulator '$avd_name'..."
  echo "Emulator log: $log_path"
  if [[ "$show_window" != "1" ]]; then
    echo "Running emulator headless. Set NOMAD_ANDROID_EMULATOR_WINDOW=1 to show the window."
  fi
  if [[ -d "$sysdir_override" ]]; then
    echo "Using emulator system image: $sysdir_override"
  fi

  for _ in {1..120}; do
    existing_serial="$(
      adb devices | awk '
        /^emulator-[0-9]+\t(device|offline)$/ { print $1 }
      ' | head -n 1
    )"
    [[ -n "$existing_serial" ]] && break
    sleep 2
  done

  [[ -n "$existing_serial" ]] || fail "Timed out waiting for emulator to appear in adb devices."
fi

adb -s "$existing_serial" wait-for-device >/dev/null

for _ in {1..180}; do
  boot_completed="$(adb -s "$existing_serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  if [[ "$boot_completed" == "1" ]]; then
    adb -s "$existing_serial" shell input keyevent 82 >/dev/null 2>&1 || true
    echo "Emulator ready on $existing_serial"
    exit 0
  fi
  sleep 2
done

fail "Timed out waiting for emulator boot completion on $existing_serial."
