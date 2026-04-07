#!/bin/zsh

if [[ -n "${NOMAD_RELEASE_COMMON_LOADED:-}" ]]; then
  return 0
fi

NOMAD_RELEASE_COMMON_LOADED=1

source "$(dirname "$0")/android-env.sh"

release_basename() {
  printf 'NomadDashboard-%s\n' "$(marketing_version)"
}

release_bundle_path() {
  printf '%s/app/build/outputs/bundle/release/app-release.aab\n' "$REPO_ROOT"
}

release_apk_path() {
  printf '%s/app/build/outputs/apk/release/app-release.apk\n' "$REPO_ROOT"
}

assert_clean_worktree() {
  local dirty_status
  dirty_status="$(git -C "$REPO_ROOT" status --short)"
  [[ -z "$dirty_status" ]] || fail $'Release commands require a clean git working tree.\nDirty paths:\n'"$dirty_status"
}

assert_signing_env() {
  load_local_env
  [[ -n "${NOMAD_KEYSTORE_PATH:-}" ]] || fail "NOMAD_KEYSTORE_PATH is not set."
  [[ -n "${NOMAD_KEYSTORE_PASSWORD:-}" ]] || fail "NOMAD_KEYSTORE_PASSWORD is not set."
  [[ -n "${NOMAD_KEY_ALIAS:-}" ]] || fail "NOMAD_KEY_ALIAS is not set."
  [[ -n "${NOMAD_KEY_PASSWORD:-}" ]] || fail "NOMAD_KEY_PASSWORD is not set."
  [[ -f "${NOMAD_KEYSTORE_PATH}" ]] || fail "Keystore not found at ${NOMAD_KEYSTORE_PATH}."
}

assert_play_env() {
  load_local_env
  [[ -n "${NOMAD_PLAY_SERVICE_ACCOUNT_JSON:-}" ]] || fail "NOMAD_PLAY_SERVICE_ACCOUNT_JSON is not set."
  [[ -f "${NOMAD_PLAY_SERVICE_ACCOUNT_JSON}" ]] || fail "Play service account file not found."
}

