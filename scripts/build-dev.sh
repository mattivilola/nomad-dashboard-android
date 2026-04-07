#!/bin/zsh
set -euo pipefail

source "$(dirname "$0")/android-env.sh"
run_gradle :app:assembleDebug

