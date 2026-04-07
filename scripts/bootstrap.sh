#!/bin/zsh
set -euo pipefail

source "$(dirname "$0")/android-env.sh"

export_android_env

log "Resolved JAVA_HOME=$JAVA_HOME"
log "Resolved ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"

[[ -d "$ANDROID_SDK_ROOT" ]] || fail "Android SDK not found at $ANDROID_SDK_ROOT"
[[ -x "$(gradlew)" ]] || fail "Missing Gradle wrapper."

run_gradle --version >/dev/null
log "Gradle wrapper is ready."

