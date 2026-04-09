#!/bin/zsh
set -euo pipefail

export NOMAD_PLAY_RELEASE_STATUS="${NOMAD_PLAY_RELEASE_STATUS:-completed}"
"$(dirname "$0")/publish-track.sh" production
