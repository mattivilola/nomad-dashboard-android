#!/bin/zsh
set -euo pipefail

source "$(dirname "$0")/android-env.sh"

"$REPO_ROOT/scripts/test-emulator.sh" "$@"
