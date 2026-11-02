#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

log_dir="${E2E_LOG_DIR:-${RUNNER_TEMP:-${TMPDIR:-/tmp}}/caparao-e2e-logs}"
mkdir -p "$log_dir"
adb="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"

capture_emulator_logs() {
  local status=$?
  trap - EXIT
  # The helper bounds every capture and scrubs serials, model/device names, addresses and tokens before the
  # workflow uploads this directory (#146). It must never change the E2E result, so failures here are ignored.
  python3 scripts/e2e-diagnostics.py collect \
    --log-dir "$log_dir" \
    --adb "$adb" \
    --serial "${ANDROID_SERIAL:-}" \
    --status "$status" \
    --heading "E2E runner diagnostics" \
    --append-emulator || true
  exit "$status"
}
trap capture_emulator_logs EXIT

E2E_LOG_DIR="$log_dir" E2E_PORT="${E2E_PORT:-8099}" scripts/e2e.sh 2>&1 | tee "$log_dir/e2e.log"

if [ "${E2E_FORCE_FAILURE:-false}" = "true" ]; then
  echo "Intentional failure after E2E passed, to verify failure log upload." >&2
  exit 1
fi
