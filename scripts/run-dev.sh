#!/bin/zsh
set -euo pipefail

source "$(dirname "$0")/android-env.sh"

run_gradle :app:installDebug
require_command adb
adb shell am start -n com.iloapps.nomaddashboard.dev/com.iloapps.nomaddashboard.MainActivity

