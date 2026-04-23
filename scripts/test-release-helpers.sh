#!/bin/zsh
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

custom_track="qa-regression-track"
env_file="$tmp_dir/Signing.env"
scripts_dir="$tmp_dir/scripts"
config_dir="$tmp_dir/Config"

mkdir -p "$scripts_dir"
mkdir -p "$config_dir"

cat >"$env_file" <<EOF
export NOMAD_PLAY_CLOSED_TRACK="$custom_track"
EOF

cp "$repo_root/Config/version.properties" "$config_dir/version.properties"
cp "$repo_root/scripts/android-env.sh" "$scripts_dir/android-env.sh"
cp "$repo_root/scripts/release-common.sh" "$scripts_dir/release-common.sh"
cp "$repo_root/scripts/release-preflight.sh" "$scripts_dir/release-preflight.sh"
cp "$repo_root/scripts/publish-closed.sh" "$scripts_dir/publish-closed.sh"

cat >"$scripts_dir/publish-track.sh" <<'EOF'
#!/bin/zsh
set -euo pipefail
printf 'stub publish-track received: %s\n' "$1"
EOF

chmod +x \
  "$scripts_dir/android-env.sh" \
  "$scripts_dir/release-common.sh" \
  "$scripts_dir/release-preflight.sh" \
  "$scripts_dir/publish-closed.sh" \
  "$scripts_dir/publish-track.sh"

preflight_output="$(
  NOMAD_SIGNING_ENV_FILE="$env_file" \
    "$scripts_dir/release-preflight.sh" --dry-run
)"

printf '%s\n' "$preflight_output" | grep -Fx "CLOSED_TRACK=$custom_track" >/dev/null || {
  printf 'Expected release-preflight to resolve CLOSED_TRACK=%s\n' "$custom_track" >&2
  exit 1
}

publish_output="$(
  NOMAD_SIGNING_ENV_FILE="$env_file" \
    "$scripts_dir/publish-closed.sh"
)"

printf '%s\n' "$publish_output" | grep -Fx "Publishing to Google Play closed track: $custom_track" >/dev/null || {
  printf 'Expected publish-closed to announce track %s\n' "$custom_track" >&2
  exit 1
}

printf '%s\n' "$publish_output" | grep -Fx "stub publish-track received: $custom_track" >/dev/null || {
  printf 'Expected publish-closed to pass track %s to publish-track.sh\n' "$custom_track" >&2
  exit 1
}

printf 'Release helper closed-track regression checks passed.\n'
