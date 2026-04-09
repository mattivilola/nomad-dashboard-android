#!/bin/zsh
set -euo pipefail

track="${NOMAD_PLAY_CLOSED_TRACK:-closed}"
"$(dirname "$0")/publish-track.sh" "$track"
