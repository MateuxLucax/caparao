#!/usr/bin/env bash
# End-to-end test on the local emulator (roadmap stage 6): the stub server runs on this machine, the app records a
# replayed espresso and uploads it, then this script checks what the stub holds. Never touches a phone: it picks
# the running emulator (or ANDROID_SERIAL). Needs Go and a running emulator.
set -euo pipefail
cd "$(dirname "$0")/.."

adb="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
app=io.github.mateuxlucax.caparao
port="${E2E_PORT:-8099}"
export ANDROID_SERIAL="${ANDROID_SERIAL:-$("$adb" devices | awk '/^emulator-/ { print $1; exit }')}"
[ -n "$ANDROID_SERIAL" ] || { echo "no emulator running (or set ANDROID_SERIAL)" >&2; exit 1; }

# A throwaway token for this run only.
token=$(openssl rand -hex 16)
tmp=$(mktemp -d)
(cd stub && go build -o "$tmp/stub" .)
AUTH_TOKEN="$token" LISTEN_ADDR="127.0.0.1:$port" "$tmp/stub" >"$tmp/stub.log" 2>&1 &
stub=$!
trap 'kill $stub 2>/dev/null; wait $stub 2>/dev/null; rm -rf "$tmp"' EXIT
for _ in $(seq 1 50); do curl -fs "http://127.0.0.1:$port/healthz" >/dev/null && break; sleep 0.2; done

./gradlew --console=plain -q assembleDebug assembleDebugAndroidTest
"$adb" install -r -t app/build/outputs/apk/debug/app-debug.apk >/dev/null
"$adb" install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null
# The stub is on this machine, which the emulator reaches at 10.0.2.2: a local network address on Android 17.
"$adb" shell pm grant "$app" android.permission.ACCESS_LOCAL_NETWORK 2>/dev/null || true

result=$("$adb" shell am instrument -w -e server_url "http://10.0.2.2:$port" -e token "$token" \
  -e class "$app.EndToEndTest" "$app.test/androidx.test.runner.AndroidJUnitRunner")
echo "$result" | grep -q "OK (1 test)" || { echo "$result" >&2; echo "--- stub log" >&2; cat "$tmp/stub.log" >&2; exit 1; }

brews=$(curl -fs -H "Authorization: Bearer $token" "http://127.0.0.1:$port/api/v1/brews")
python3 - "$brews" <<'PY'
import json, sys
brews = json.loads(sys.argv[1])
assert len(brews) == 1, f"expected one brew, got {len(brews)}"
b = brews[0]
assert b["mode"] == "espresso", b
assert abs(b["dose_g"] - 18.0) < 1e-9, b
assert abs(b["yield_g"] - 36.0) <= 0.5, b
assert b["sample_count"] > 100, b
assert not b["interrupted"], b
print(f"e2e passed: espresso {b['dose_g']} g -> {b['yield_g']} g, {b['sample_count']} samples on the stub")
PY
