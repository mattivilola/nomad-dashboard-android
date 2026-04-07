#!/bin/zsh
set -euo pipefail

source "$(dirname "$0")/android-env.sh"

require_command adb

"$REPO_ROOT/scripts/build-dev.sh"

apk_path="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$apk_path" ]] || fail "Debug APK not found at $apk_path"

install_output="$(adb install -r "$apk_path" 2>&1)" || {
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

echo "$install_output"
adb shell am start -n com.iloapps.nomaddashboard.dev/com.iloapps.nomaddashboard.MainActivity
