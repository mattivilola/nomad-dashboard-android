#!/bin/zsh

if [[ -n "${NOMAD_ANDROID_ENV_LOADED:-}" ]]; then
  return 0
fi

NOMAD_ANDROID_ENV_LOADED=1

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CONFIG_DIR="$REPO_ROOT/Config"
VERSION_FILE="$CONFIG_DIR/version.properties"
DEFAULT_SIGNING_ENV_FILE="$CONFIG_DIR/Signing.env"
DEFAULT_APP_CONFIG_ENV_FILE="$CONFIG_DIR/AppConfig.env"
ARTIFACTS_ROOT="$REPO_ROOT/artifacts"

fail() {
  echo "$1" >&2
  exit 1
}

log() {
  echo "$1"
}

require_command() {
  local command_name="$1"
  command -v "$command_name" >/dev/null 2>&1 || fail "Required command not found: $command_name"
}

load_optional_env_file() {
  local path="$1"
  if [[ -f "$path" ]]; then
    # shellcheck disable=SC1090
    source "$path"
  fi
}

load_local_env() {
  load_optional_env_file "${NOMAD_SIGNING_ENV_FILE:-$DEFAULT_SIGNING_ENV_FILE}"
  load_optional_env_file "${NOMAD_APP_CONFIG_ENV_FILE:-$DEFAULT_APP_CONFIG_ENV_FILE}"
}

android_studio_jdk() {
  local candidates=(
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    "/Applications/Android Studio.app/Contents/jbr"
  )

  for candidate in "${candidates[@]}"; do
    [[ -x "$candidate/bin/java" ]] && printf '%s\n' "$candidate" && return 0
  done

  return 1
}

resolved_java_home() {
  if [[ -n "${JAVA_HOME:-}" ]] && [[ -x "$JAVA_HOME/bin/java" ]]; then
    local version
    version="$("$JAVA_HOME/bin/java" -version 2>&1 | sed -n '1s/.*version "\(.*\)".*/\1/p')"
    if [[ "$version" == 17* || "$version" == 21* ]]; then
      printf '%s\n' "$JAVA_HOME"
      return 0
    fi
  fi

  android_studio_jdk || fail "Could not find a Java 17+ runtime. Install Android Studio or set JAVA_HOME."
}

export_android_env() {
  export JAVA_HOME
  JAVA_HOME="$(resolved_java_home)"
  export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
  export ANDROID_HOME="$ANDROID_SDK_ROOT"
  export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$REPO_ROOT/.android-home}"
  export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$REPO_ROOT/.gradle-home}"
  export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"
}

version_value() {
  local key="$1"
  sed -n "s/^${key}=//p" "$VERSION_FILE" | head -n 1
}

marketing_version() {
  version_value "MARKETING_VERSION"
}

version_code() {
  version_value "VERSION_CODE"
}

release_tag() {
  printf 'v%s\n' "$(marketing_version)"
}

ensure_artifacts_dir() {
  mkdir -p "$ARTIFACTS_ROOT"
}

gradlew() {
  printf '%s/gradlew\n' "$REPO_ROOT"
}

run_gradle() {
  export_android_env
  load_local_env
  "$(gradlew)" "$@"
}

adb_connected_device_serials() {
  adb devices | awk '
    NR > 1 && $2 == "device" { print $1 }
  '
}

first_physical_device_serial() {
  adb_connected_device_serials | awk '
    $1 !~ /^emulator-/ { print $1; exit }
  '
}

first_emulator_serial() {
  adb_connected_device_serials | awk '
    $1 ~ /^emulator-/ { print $1; exit }
  '
}

require_adb_target_serial() {
  if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    local requested_serial="$ANDROID_SERIAL"
    adb_connected_device_serials | grep -Fx -- "$requested_serial" >/dev/null 2>&1 || fail "ANDROID_SERIAL is set to '$requested_serial', but that target is not connected in adb."
    printf '%s\n' "$requested_serial"
    return 0
  fi

  local physical_serial
  physical_serial="$(first_physical_device_serial)"
  if [[ -n "$physical_serial" ]]; then
    printf '%s\n' "$physical_serial"
    return 0
  fi

  local emulator_serial
  emulator_serial="$(first_emulator_serial)"
  if [[ -n "$emulator_serial" ]]; then
    printf '%s\n' "$emulator_serial"
    return 0
  fi

  fail "No connected Android device or emulator found."
}
