#!/bin/zsh
set -euo pipefail

source "$(dirname "$0")/android-env.sh"

require_command adb
adb shell am force-stop com.iloapps.nomaddashboard.dev || true
"$REPO_ROOT/scripts/run-dev.sh"

