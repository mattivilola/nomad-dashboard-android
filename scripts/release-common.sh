#!/bin/zsh

if [[ -n "${NOMAD_RELEASE_COMMON_LOADED:-}" ]]; then
  return 0
fi

NOMAD_RELEASE_COMMON_LOADED=1

source "$(dirname "$0")/android-env.sh"

CHANGELOG_FILE="$REPO_ROOT/CHANGELOG.md"
PLAY_RELEASE_NOTES_FILE="$REPO_ROOT/app/src/main/play/release-notes/en-US/default.txt"

release_basename() {
  printf 'NomadDashboard-%s\n' "$(marketing_version)"
}

release_bundle_path() {
  printf '%s/app/build/outputs/bundle/release/app-release.aab\n' "$REPO_ROOT"
}

release_apk_path() {
  printf '%s/app/build/outputs/apk/release/app-release.apk\n' "$REPO_ROOT"
}

changelog_file() {
  printf '%s\n' "$CHANGELOG_FILE"
}

play_release_notes_file() {
  printf '%s\n' "$PLAY_RELEASE_NOTES_FILE"
}

latest_release_tag() {
  git -C "$REPO_ROOT" describe --tags --abbrev=0 --match 'v*' 2>/dev/null || true
}

assert_tag_absent() {
  local tag_name="$1"

  git -C "$REPO_ROOT" rev-parse -q --verify "refs/tags/$tag_name" >/dev/null 2>&1 && fail "Git tag already exists: $tag_name"
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
