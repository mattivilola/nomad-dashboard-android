#!/bin/zsh
set -euo pipefail

source "$(dirname "$0")/android-env.sh"
run_gradle :app:bundleRelease

# Google Play reads native debug symbols from the bundle itself (AGP embeds them when the NDK pinned by
# ndkVersion is installed). Libraries that ship already stripped, like the AndroidX ones bundled today,
# have no symbols to give; any other .so without embedded symbols means extraction silently failed, so
# stop before upload.
bundle_path="$REPO_ROOT/app/build/outputs/bundle/release/app-release.aab"
[[ -f "$bundle_path" ]] || fail "Release bundle not found at $bundle_path"
bundle_entries="$(unzip -Z1 "$bundle_path")"
native_libs=("${(@f)$(grep '/lib/.*\.so$' <<<"$bundle_entries" || true)}")
native_libs=(${native_libs:#})
if (( ${#native_libs} > 0 )); then
  if grep -q '^BUNDLE-METADATA/com.android.tools.build.debugsymbols/' <<<"$bundle_entries"; then
    log "Native debug symbols are embedded in the release bundle."
  else
    lib_dir="$(mktemp -d)"
    trap 'rm -rf "$lib_dir"' EXIT
    unzip -q "$bundle_path" "${native_libs[@]}" -d "$lib_dir"
    unstripped=()
    for lib in "${native_libs[@]}"; do
      file -b "$lib_dir/$lib" | grep -q ', stripped' || unstripped+=("$lib")
    done
    (( ${#unstripped} == 0 )) ||
      fail "Native libraries with symbols have no embedded debug symbols: ${unstripped[*]}. Install the NDK set by ndkVersion in app/build.gradle.kts."
    log "Bundled native libraries ship already stripped upstream, so there are no debug symbols to embed. Play will keep warning about them."
  fi
fi
