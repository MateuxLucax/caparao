#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

log_dir="${E2E_LOG_DIR:-${RUNNER_TEMP:-${TMPDIR:-/tmp}}/caparao-e2e-logs}"
raw_log_dir="${E2E_RAW_LOG_DIR:-${RUNNER_TEMP:-${TMPDIR:-/tmp}}/caparao-e2e-raw-logs}"
mkdir -p "$raw_log_dir"
adb="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
token=$(openssl rand -hex 16)

capture_emulator_logs() {
  local status=$?
  trap - EXIT
  # Raw E2E logs stay in a private directory. The helper publishes a complete sanitized set only after every
  # source log has been read and sanitized; a helper failure must preserve the E2E result and publish no raw file.
  diagnostic_args=(collect \
    --log-dir "$log_dir" \
    --raw-dir "$raw_log_dir" \
    --adb "$adb" \
    --serial "${ANDROID_SERIAL:-}" \
    --status "$status" \
    --heading "E2E runner diagnostics" \
    --append-emulator)
  if [ "$status" -ne 0 ]; then
    diagnostic_args+=(--runtime-log)
  fi
  if ! E2E_DIAGNOSTICS_SENSITIVE="$token" python3 scripts/e2e-diagnostics.py "${diagnostic_args[@]}"; then
    echo "warning: E2E diagnostic sanitization failed; no raw logs were published" >&2
  fi
  unset token
  exit "$status"
}
trap capture_emulator_logs EXIT

E2E_AUTH_TOKEN="$token" E2E_RAW_LOG_DIR="$raw_log_dir" E2E_PORT="${E2E_PORT:-8099}" \
  scripts/e2e.sh 2>&1 | tee "$raw_log_dir/e2e.log"

if [ "${E2E_FORCE_FAILURE:-false}" = "true" ]; then
  echo "Intentional failure after E2E passed, to verify failure log upload." >&2
  exit 1
fi
