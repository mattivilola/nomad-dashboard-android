#!/bin/zsh
set -euo pipefail

source "$(dirname "$0")/android-env.sh"
load_local_env

local_ip_response="$(curl -s https://free.freeipapi.com/api/json/)"
echo "FreeIPAPI:"
echo "$local_ip_response"

latitude="${1:-37.7749}"
longitude="${2:--122.4194}"
echo
echo "Open-Meteo:"
curl -s "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation_probability,wind_speed_10m,wind_direction_10m&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto"

echo
echo "Smartraveller:"
curl -s \
  -H 'Accept: text/html,application/xhtml+xml' \
  -H 'Accept-Language: en-AU,en;q=0.9' \
  -H 'User-Agent: Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36' \
  https://www.smartraveller.gov.au/destinations | head -c 800 && echo

if [[ -n "${NOMAD_RELIEFWEB_APP_NAME:-}" ]]; then
  echo
  echo "ReliefWeb:"
  curl -s "https://api.reliefweb.int/v2/reports?appname=${NOMAD_RELIEFWEB_APP_NAME}&limit=1" | head -c 800 && echo
else
  echo
  echo "ReliefWeb skipped: NOMAD_RELIEFWEB_APP_NAME not set."
fi
