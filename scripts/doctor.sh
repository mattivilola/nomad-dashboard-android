#!/bin/zsh
set -euo pipefail

source "$(dirname "$0")/android-env.sh"

export_android_env
load_local_env

cat <<EOF
REPO_ROOT=$REPO_ROOT
JAVA_HOME=$JAVA_HOME
ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-}
MARKETING_VERSION=$(marketing_version)
VERSION_CODE=$(version_code)
APPLICATION_ID=${NOMAD_APPLICATION_ID:-com.iloapps.nomaddashboard}
PLAY_TRACK=${NOMAD_PLAY_TRACK:-internal}
EOF

run_gradle --version

