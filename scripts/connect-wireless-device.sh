#!/bin/zsh
set -euo pipefail

source "$(dirname "$0")/android-env.sh"

export_android_env
require_command adb

pair_endpoint="${1:-}"
connect_endpoint="${2:-}"

if [[ -z "$pair_endpoint" ]]; then
  printf 'Pair endpoint (PHONE_IP:PAIR_PORT): '
  read -r pair_endpoint
fi

if [[ -z "$connect_endpoint" ]]; then
  printf 'Connect endpoint (PHONE_IP:CONNECT_PORT): '
  read -r connect_endpoint
fi

[[ "$pair_endpoint" == *:* ]] || fail "Pair endpoint must look like PHONE_IP:PAIR_PORT"
[[ "$connect_endpoint" == *:* ]] || fail "Connect endpoint must look like PHONE_IP:CONNECT_PORT"

echo "Pairing with $pair_endpoint"
adb pair "$pair_endpoint"

echo
echo "Connecting to $connect_endpoint"
adb connect "$connect_endpoint"

echo
echo "Connected devices:"
adb devices -l

