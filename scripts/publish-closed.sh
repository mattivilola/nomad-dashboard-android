#!/bin/zsh
set -euo pipefail

source "$(dirname "$0")/release-common.sh"
load_local_env

track="${NOMAD_PLAY_CLOSED_TRACK:-closed}"
printf 'Publishing to Google Play closed track: %s\n' "$track"
"$(dirname "$0")/publish-track.sh" "$track"
