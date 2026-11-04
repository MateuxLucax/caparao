#!/usr/bin/env bash
# End-to-end test on the local emulator (#166): a pinned real exporter
# (scripts/exporter.sh) runs on this machine, the app records a replayed
# espresso and French press and uploads them, then this script checks what
# SQLite holds (list + curves). Never touches a phone: it picks the running
# emulator (or ANDROID_SERIAL). Needs Go and a running emulator.
#
#   scripts/e2e.sh                       against the pinned exporter
#   E2E_LOG_DIR=/tmp/e2e-logs            publishes sanitized server and instrumentation logs
#
# Prometheus remote-write coverage lives in the exporter's integration
# suite; this script validates the app-facing API and persistence only.
#
# Failure diagnostics are filtered to AndroidRuntime:E (all other tags silent), bounded, and scrubbed of device
# serials, model names, addresses and tokens by scripts/e2e-diagnostics.py before they are uploaded (#146).
set -euo pipefail
cd "$(dirname "$0")/.."

adb="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
app=io.github.mateuxlucax.caparao.debug
port="${E2E_PORT:-8099}"
export ANDROID_SERIAL="${ANDROID_SERIAL:-$("$adb" devices | awk '/^emulator-/ { print $1; exit }')}"
[ -n "$ANDROID_SERIAL" ] || { echo "no emulator running (or set ANDROID_SERIAL)" >&2; exit 1; }

# A throwaway token for this run only. The CI wrapper can provide it so it can redact the complete artifact set.
token="${E2E_AUTH_TOKEN:-$(openssl rand -hex 16)}"
unset E2E_AUTH_TOKEN
log_dir="${E2E_LOG_DIR:-}"
raw_log_dir="${E2E_RAW_LOG_DIR:-}"
tmp=$(mktemp -d)
pid=""
cleanup() {
  status=$?
  trap - EXIT
  if [ -n "$pid" ]; then
    kill "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
  fi
  if [ -n "$raw_log_dir" ]; then
    if mkdir -p "$raw_log_dir"; then
      [ ! -f "$tmp/server.log" ] || cp "$tmp/server.log" "$raw_log_dir/server.log" || \
        echo "warning: could not preserve the raw server log for private sanitization" >&2
      [ ! -f "$tmp/instrumentation.log" ] || cp "$tmp/instrumentation.log" "$raw_log_dir/instrumentation.log" || \
        echo "warning: could not preserve the raw instrumentation log for private sanitization" >&2
    else
      echo "warning: could not create the private E2E log directory" >&2
    fi
  elif [ -n "$log_dir" ]; then
    diagnostic_args=(collect --raw-dir "$tmp" --log-dir "$log_dir" --adb "$adb" \
      --serial "$ANDROID_SERIAL" --only-file server.log --only-file instrumentation.log)
    if [ "$status" -ne 0 ]; then
      diagnostic_args+=(--status "$status" --runtime-log)
    fi
    if ! E2E_DIAGNOSTICS_SENSITIVE="$token" python3 scripts/e2e-diagnostics.py "${diagnostic_args[@]}"; then
      echo "warning: E2E diagnostic sanitization failed; no raw logs were published" >&2
    fi
  fi
  rm -rf "$tmp"
  exit "$status"
}
trap cleanup EXIT

scripts/exporter.sh build
AUTH_TOKEN="$token" EXPORTER_PORT="$port" scripts/exporter.sh start "$tmp" &
pid=$!
for _ in $(seq 1 50); do curl -fs "http://127.0.0.1:$port/healthz" >/dev/null && break; sleep 0.2; done
curl -fs "http://127.0.0.1:$port/healthz" >/dev/null || { echo "exporter did not become ready" >&2; cat "$tmp/server.log" >&2; exit 1; }

./gradlew --console=plain -q assembleDebug assembleDebugAndroidTest
"$adb" install -r -t app/build/outputs/apk/debug/app-debug.apk >/dev/null
"$adb" install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null
# The exporter is on this machine, which the emulator reaches at 10.0.2.2: a local network address on Android 17.
"$adb" shell pm grant "$app" android.permission.ACCESS_LOCAL_NETWORK 2>/dev/null || true

instrumentation_status=0
if result=$("$adb" shell am instrument -w -e server_url "http://10.0.2.2:$port" -e token "$token" \
  -e class io.github.mateuxlucax.caparao.EndToEndTest "$app.test/androidx.test.runner.AndroidJUnitRunner"); then
  :
else
  instrumentation_status=$?
fi
printf '%s\n' "$result" > "$tmp/instrumentation.log"
if [ "$instrumentation_status" -ne 0 ]; then
  echo "$result" >&2
  echo "--- exporter log" >&2
  cat "$tmp/server.log" >&2
  exit "$instrumentation_status"
fi
echo "$result" | grep -q "OK (2 tests)" || { echo "$result" >&2; echo "--- exporter log" >&2; cat "$tmp/server.log" >&2; exit 1; }

brews=$(curl -fs -H "Authorization: Bearer $token" "http://127.0.0.1:$port/api/v1/brews")
python3 - "$brews" "http://127.0.0.1:$port" "$token" <<'PY'
import json, sys, urllib.request

brews = json.loads(sys.argv[1])
base, token = sys.argv[2], sys.argv[3]
assert len(brews) == 2, f"expected espresso and French press, got {len(brews)}"
by_mode = {b["mode"]: b for b in brews}
assert set(by_mode) == {"espresso", "french_press"}, by_mode
espresso = by_mode["espresso"]
assert abs(espresso["dose_g"] - 18.0) < 1e-9, espresso
assert abs(espresso["yield_g"] - 36.0) <= 0.5, espresso
assert espresso["sample_count"] > 100, espresso
assert not espresso["interrupted"], espresso
press = by_mode["french_press"]
assert abs(press["dose_g"] - 20.0) < 1e-9, press
assert abs(press["yield_g"] - 300.0) <= 0.5, press
assert press["sample_count"] > 2, press
assert not press["interrupted"], press

def curve(brew_id):
    req = urllib.request.Request(base + f"/api/v1/brews/{brew_id}",
        headers={"Authorization": f"Bearer {token}"})
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.load(resp)

for name, brew in (("espresso", espresso), ("french_press", press)):
    full = curve(brew["id"])
    assert full["mode"] == brew["mode"], (name, full)
    assert len(full["samples"]) == brew["sample_count"], (name, full["sample_count"], brew["sample_count"])
    assert full["samples"], (name, "empty curve read back")
    weights = [s["weight_g"] for s in full["samples"]]
    assert max(weights) >= brew["yield_g"] - 0.5, (name, weights[-3:])
    times = [s["t_ms"] for s in full["samples"]]
    assert times == sorted(times), (name, "curve out of upload order")
print(f"exporter holds espresso {espresso['dose_g']} g -> {espresso['yield_g']} g ({espresso['sample_count']} samples) and French press {press['dose_g']} g -> {press['yield_g']} g ({press['sample_count']} samples), curves read back")
PY

echo "e2e passed against the pinned exporter"
