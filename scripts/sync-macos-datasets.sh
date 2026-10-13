#!/bin/zsh
# Copies the shared offline safety datasets from a checkout of the macOS app into this repo.
#
# Today that is the Wikidata diplomatic-missions JSON (embassies and consulates). The Wikidata
# builder lives in the macOS repo (`scripts/update-diplomatic-missions.sh`); run it there first,
# then run this script to pull the result. The emergency-number and consular-contact tables are
# Kotlin sources (`core/travel/.../safety/EmergencyNumbers.kt`, `ConsularContacts.kt`) and are
# kept in step with the macOS Swift tables by hand.
#
# Dataset corrections must land in both apps: change the macOS source of truth, sync here, and
# update the matching Android tests.
#
# Usage: scripts/sync-macos-datasets.sh
#   NOMAD_MACOS_REPO  path to the macOS checkout (default: ../nomad-dashboard-macosx next to this repo)

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MACOS_REPO="${NOMAD_MACOS_REPO:-$REPO_ROOT/../nomad-dashboard-macosx}"

MISSIONS_SOURCE="$MACOS_REPO/Packages/NomadCore/Sources/NomadCore/Resources/diplomatic-missions.json"
MISSIONS_TARGET="$REPO_ROOT/core/travel/src/main/resources/diplomatic-missions.json"

if [[ ! -f "$MISSIONS_SOURCE" ]]; then
  echo "diplomatic-missions.json not found at: $MISSIONS_SOURCE" >&2
  echo "Set NOMAD_MACOS_REPO to your macOS checkout." >&2
  exit 1
fi

# Prints "<mission count> missions, generated <date>" for a missions file.
describe_dataset() {
  /usr/bin/python3 -I -c '
import json, sys
with open(sys.argv[1], encoding="utf-8") as handle:
    dataset = json.load(handle)
print("%d missions, generated %s, source %s" % (len(dataset["missions"]), dataset["generatedAt"], dataset["source"]))
' "$1"
}

new_description="$(describe_dataset "$MISSIONS_SOURCE")"

if [[ -f "$MISSIONS_TARGET" ]] && cmp -s "$MISSIONS_SOURCE" "$MISSIONS_TARGET"; then
  echo "diplomatic-missions.json is already up to date ($new_description)."
  exit 0
fi

if [[ -f "$MISSIONS_TARGET" ]]; then
  echo "before: $(describe_dataset "$MISSIONS_TARGET")"
else
  mkdir -p "$(dirname "$MISSIONS_TARGET")"
  echo "before: (not present)"
fi

cp "$MISSIONS_SOURCE" "$MISSIONS_TARGET"
echo "after:  $new_description"
echo "Updated core/travel/src/main/resources/diplomatic-missions.json from $MACOS_REPO"
echo "Next: update the mission count in DiplomaticMissionsTest if it changed, then run :core:travel:testDebugUnitTest."
