#!/bin/zsh
set -euo pipefail

source "$(dirname "$0")/android-env.sh"

require_command adb

progress_interval="${NOMAD_RUN_PROGRESS_INTERVAL_SECONDS:-3}"
verbose_mode="${NOMAD_RUN_VERBOSE:-0}"

log_step() {
  printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$1"
}

run_with_progress() {
  local label="$1"
  local output_file="$2"
  shift 2

  local start_time=$SECONDS
  local last_reported_elapsed=-1

  if [[ "$verbose_mode" == "1" ]]; then
    log_step "Running: $*"
  fi

  "$@" >"$output_file" 2>&1 &
  local command_pid=$!

  while kill -0 "$command_pid" >/dev/null 2>&1; do
    local elapsed=$((SECONDS - start_time))
    if (( progress_interval > 0 && elapsed > 0 && elapsed % progress_interval == 0 && elapsed != last_reported_elapsed )); then
      log_step "$label still in progress... ${elapsed}s elapsed"
      last_reported_elapsed=$elapsed
    fi
    sleep 1
  done

  wait "$command_pid"
}

"$REPO_ROOT/scripts/build-dev.sh"

apk_path="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$apk_path" ]] || fail "Debug APK not found at $apk_path"

target_serial="$(require_adb_target_serial)"
apk_size="$(du -h "$apk_path" | awk '{print $1}')"
install_output_file="$(mktemp)"
launch_output_file="$(mktemp)"
trap 'rm -f "$install_output_file" "$launch_output_file"' EXIT

if [[ "$target_serial" == emulator-* ]]; then
  log_step "Installing and launching on emulator $target_serial"
else
  log_step "Installing and launching on physical device $target_serial"
fi

log_step "Starting APK install (${apk_size}, adb install -r). Wireless ADB and Package Manager may take a while."
run_with_progress "APK install" "$install_output_file" adb -s "$target_serial" install -r "$apk_path" || {
  install_output="$(cat "$install_output_file")"
  echo "$install_output" >&2
  if [[ "$install_output" == *"INSTALL_FAILED_USER_RESTRICTED"* ]]; then
    cat >&2 <<'EOF'

Install was blocked by the device, not by the Android app build.

Common Xiaomi / MIUI / HyperOS fixes:
1. On the phone, go to Developer options.
2. Turn on "Install via USB".
3. If available, also turn on "USB debugging (Security settings)".
4. If the phone shows an install confirmation or security prompt, accept it.
5. If it still fails, temporarily disable "MIUI optimization" / "HyperOS optimization", then retry.
6. Retry: make run

If you use wireless debugging, keep the phone unlocked and on the same Wi-Fi while installing.
EOF
  fi
  exit 1
}

install_output="$(cat "$install_output_file")"
echo "$install_output"
log_step "Starting app launch and waiting for ActivityManager timing..."
run_with_progress \
  "App launch" \
  "$launch_output_file" \
  adb -s "$target_serial" shell am start -W -n com.iloapps.nomaddashboard.dev/com.iloapps.nomaddashboard.MainActivity

cat "$launch_output_file"
