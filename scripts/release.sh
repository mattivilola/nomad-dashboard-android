#!/bin/zsh
set -euo pipefail

"$(dirname "$0")/release-preflight.sh"
"$(dirname "$0")/bundle-release.sh"
"$(dirname "$0")/publish-internal.sh"

