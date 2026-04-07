#!/bin/zsh
set -euo pipefail

source "$(dirname "$0")/release-common.sh"

assert_signing_env
assert_play_env
run_gradle :app:publishReleaseBundle

