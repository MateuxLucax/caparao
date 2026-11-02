#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

log_dir="${E2E_LOG_DIR:-${RUNNER_TEMP:-${TMPDIR:-/tmp}}/caparao-e2e-logs}"
mkdir -p "$log_dir"
adb="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"

capture_emulator_logs() {
  local status=$?
  trap - EXIT
  {
    printf '\nE2E runner exit status: %s\n' "$status"
    if [ -x "$adb" ]; then
      "$adb" devices -l
      local serial="${ANDROID_SERIAL:-$("$adb" devices | awk '/^emulator-/ { print $1; exit }')}"
      if [ -n "$serial" ]; then
        "$adb" -s "$serial" logcat -d -v threadtime -t 5000
      else
        echo "No emulator serial was available for logcat."
      fi
    else
      echo "adb was unavailable at $adb."
    fi
  } >> "$log_dir/emulator.log" 2>&1 || true
  exit "$status"
}
trap capture_emulator_logs EXIT

E2E_LOG_DIR="$log_dir" E2E_PORT="${E2E_PORT:-8099}" scripts/e2e.sh 2>&1 | tee "$log_dir/e2e.log"

if [ "${E2E_FORCE_FAILURE:-false}" = "true" ]; then
  echo "Intentional failure after E2E passed, to verify failure log upload." >&2
  exit 1
fi
