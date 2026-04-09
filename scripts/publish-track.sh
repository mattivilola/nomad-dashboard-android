#!/bin/zsh
set -euo pipefail

source "$(dirname "$0")/release-common.sh"

if [[ $# -lt 1 ]]; then
  fail "Usage: $(basename "$0") <track>"
fi

track="$1"

assert_signing_env
assert_play_env

export NOMAD_PLAY_TRACK="$track"
run_gradle :app:publishReleaseBundle
