#!/bin/zsh
set -euo pipefail

track="${NOMAD_PLAY_CLOSED_TRACK:-closed}"
printf 'Publishing to Google Play closed track: %s\n' "$track"
"$(dirname "$0")/publish-track.sh" "$track"
