#!/bin/zsh
set -euo pipefail

source "$(dirname "$0")/release-common.sh"

export_android_env

if [[ "${1:-}" == "--dry-run" ]]; then
  load_local_env
  cat <<EOF
MARKETING_VERSION=$(marketing_version)
VERSION_CODE=$(version_code)
RELEASE_TAG=$(release_tag)
BUNDLE_PATH=$(release_bundle_path)
APK_PATH=$(release_apk_path)
PLAY_TRACK=${NOMAD_PLAY_TRACK:-internal}
KEYSTORE_PATH=${NOMAD_KEYSTORE_PATH:-unset}
PLAY_SERVICE_ACCOUNT_JSON=${NOMAD_PLAY_SERVICE_ACCOUNT_JSON:-unset}
EOF
  exit 0
fi

assert_clean_worktree
assert_signing_env
assert_play_env
run_gradle :app:validateSigningRelease

