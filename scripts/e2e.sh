#!/usr/bin/env bash
# End-to-end test on the local emulator (roadmap stages 6 and 8): a server runs on this machine, the app records a
# replayed espresso and uploads it, then this script checks what the server holds. Never touches a phone: it picks
# the running emulator (or ANDROID_SERIAL). Needs Go and a running emulator.
#
#   scripts/e2e.sh                       against the stub (stub/)
#   E2E_SERVER=exporter scripts/e2e.sh   against timemore-dot-prometheus-exporter (EXPORTER_DIR, default: a sibling
#                                        checkout) with a throwaway Prometheus in Docker; also checks the metrics
set -euo pipefail
cd "$(dirname "$0")/.."

adb="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
app=io.github.mateuxlucax.caparao.debug
port="${E2E_PORT:-8099}"
export ANDROID_SERIAL="${ANDROID_SERIAL:-$("$adb" devices | awk '/^emulator-/ { print $1; exit }')}"
[ -n "$ANDROID_SERIAL" ] || { echo "no emulator running (or set ANDROID_SERIAL)" >&2; exit 1; }

# A throwaway token for this run only.
token=$(openssl rand -hex 16)
server="${E2E_SERVER:-stub}"
prometheus=caparao-e2e-prometheus
prom_port="${E2E_PROMETHEUS_PORT:-19091}"
tmp=$(mktemp -d)
cleanup() {
  kill "$pid" 2>/dev/null; wait "$pid" 2>/dev/null
  [ "$server" = exporter ] && docker rm -f "$prometheus" >/dev/null 2>&1
  rm -rf "$tmp"
}
if [ "$server" = exporter ]; then
  exporter_dir="${EXPORTER_DIR:-../timemore-dot-prometheus-exporter}"
  docker rm -f "$prometheus" >/dev/null 2>&1 || true
  docker run -d --rm --name "$prometheus" -p "127.0.0.1:$prom_port:9090" \
    -v "$(cd "$exporter_dir" && pwd)/prometheus.yml:/etc/prometheus/prometheus.yml:ro" prom/prometheus:v3.5.0 \
    --config.file=/etc/prometheus/prometheus.yml --web.enable-remote-write-receiver >/dev/null
  until curl -fs "http://127.0.0.1:$prom_port/-/ready" >/dev/null; do sleep 1; done
  (cd "$exporter_dir" && go build -o "$tmp/server" .)
  AUTH_TOKEN="$token" LISTEN_ADDR="127.0.0.1:$port" DB_PATH="$tmp/caparao.db" SYNC_INTERVAL=2s \
    REMOTE_WRITE_URL="http://127.0.0.1:$prom_port/api/v1/write" "$tmp/server" >"$tmp/server.log" 2>&1 &
else
  (cd stub && go build -o "$tmp/server" .)
  AUTH_TOKEN="$token" LISTEN_ADDR="127.0.0.1:$port" "$tmp/server" >"$tmp/server.log" 2>&1 &
fi
pid=$!
trap cleanup EXIT
for _ in $(seq 1 50); do curl -fs "http://127.0.0.1:$port/healthz" >/dev/null && break; sleep 0.2; done

./gradlew --console=plain -q assembleDebug assembleDebugAndroidTest
"$adb" install -r -t app/build/outputs/apk/debug/app-debug.apk >/dev/null
"$adb" install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null
# The stub is on this machine, which the emulator reaches at 10.0.2.2: a local network address on Android 17.
"$adb" shell pm grant "$app" android.permission.ACCESS_LOCAL_NETWORK 2>/dev/null || true

result=$("$adb" shell am instrument -w -e server_url "http://10.0.2.2:$port" -e token "$token" \
  -e class io.github.mateuxlucax.caparao.EndToEndTest "$app.test/androidx.test.runner.AndroidJUnitRunner")
echo "$result" | grep -q "OK (1 test)" || { echo "$result" >&2; echo "--- $server log" >&2; cat "$tmp/server.log" >&2; exit 1; }

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
print(f"server holds the espresso: {b['dose_g']} g -> {b['yield_g']} g, {b['sample_count']} samples")
PY

if [ "$server" = exporter ]; then
  # The exporter syncs every 2 s here; the summary is stamped at the brew's end, a few minutes ago at most.
  for _ in $(seq 1 30); do
    yield=$(curl -fs "http://127.0.0.1:$prom_port/api/v1/query" --data-urlencode 'query=caparao_brew_yield_grams{mode="espresso"}' \
      | python3 -c 'import json,sys; r=json.load(sys.stdin)["data"]["result"]; print(r[0]["value"][1] if r else "")')
    [ -n "$yield" ] && break
    sleep 1
  done
  [ -n "$yield" ] || { echo "no caparao_brew_yield_grams in Prometheus" >&2; cat "$tmp/server.log" >&2; exit 1; }
  points=$(curl -fs "http://127.0.0.1:$prom_port/api/v1/query" --data-urlencode 'query=count_over_time(caparao_brew_weight_grams[10m])' \
    | python3 -c 'import json,sys; r=json.load(sys.stdin)["data"]["result"]; print(r[0]["value"][1] if r else 0)')
  echo "Prometheus has the espresso: yield $yield g, $points curve points"
fi
echo "e2e passed against the $server"
