#!/bin/zsh
set -euo pipefail

source "$(dirname "$0")/release-common.sh"

usage() {
  echo "Usage: $0 patch|minor|major" >&2
  exit 1
}

part="${1:-}"
[[ -n "$part" ]] || usage

assert_clean_worktree

current_version="$(marketing_version)"
current_code="$(version_code)"

IFS='.' read -r major minor patch <<<"$current_version"

case "$part" in
  patch) patch=$((patch + 1)) ;;
  minor) minor=$((minor + 1)); patch=0 ;;
  major) major=$((major + 1)); minor=0; patch=0 ;;
  *) usage ;;
esac

next_version="$major.$minor.$patch"
next_code=$((current_code + 1))
next_tag="v$next_version"
release_date="$(date +%F)"
current_tag="$(release_tag)"

assert_tag_absent "$next_tag"

if [[ -z "$(latest_release_tag)" ]] && [[ -f "$(changelog_file)" ]] && grep -Fq "## [$current_version] -" "$(changelog_file)"; then
  fail "No git release tag exists for the current changelog baseline. Backfill $current_tag before preparing $next_version."
fi

python3 "$(dirname "$0")/generate-release-artifacts.py" \
  --version "$next_version" \
  --release-date "$release_date" \
  --changelog "$(changelog_file)" \
  --play-notes "$(play_release_notes_file)"

python3 - <<PY
from pathlib import Path
path = Path("$VERSION_FILE")
lines = []
for raw in path.read_text().splitlines():
    if raw.startswith("MARKETING_VERSION="):
        lines.append(f"MARKETING_VERSION=$next_version")
    elif raw.startswith("VERSION_CODE="):
        lines.append(f"VERSION_CODE=$next_code")
    else:
        lines.append(raw)
path.write_text("\\n".join(lines) + "\\n")
PY

git -C "$REPO_ROOT" add "$VERSION_FILE" "$(changelog_file)" "$(play_release_notes_file)"
git -C "$REPO_ROOT" commit -m "Release v$next_version"
git -C "$REPO_ROOT" tag "$next_tag"
