#!/bin/zsh
set -euo pipefail

source "$(dirname "$0")/release-common.sh"

if [[ $# -lt 1 ]]; then
  fail "Usage: $(basename "$0") <track>"
fi

track="$1"
release_status_override="${NOMAD_PLAY_RELEASE_STATUS:-}"

assert_signing_env
assert_play_env

export_android_env
load_local_env

export NOMAD_PLAY_TRACK="$track"
if [[ -n "$release_status_override" ]]; then
  export NOMAD_PLAY_RELEASE_STATUS="$release_status_override"
fi

"$(gradlew)" :app:publishReleaseBundle
