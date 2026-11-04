#!/usr/bin/env bash
# Local gate before pushing: JVM unit tests, Android lint, debug build, a compile of the instrumented tests, the
# capture and E2E diagnostics script tests, the contract/ fixtures against the pinned exporter and a secret scan.
# Mirrors the CI job, so a green run here should be a green run there (#99).
set -euo pipefail
cd "$(dirname "$0")/.."

start=$(date +%s)
./gradlew --console=plain createDebugUnitTestCoverageReport :wear:testDebugUnitTest :wearlink:test lintDebug assembleDebug compileDebugAndroidTestKotlin
python3 -m unittest discover -s scripts/tests -p 'test_*.py'

token="contract-check-$(date +%s)-$$"
tmp=$(mktemp -d)
scripts/exporter.sh build
AUTH_TOKEN="$token" EXPORTER_PORT=8098 scripts/exporter.sh start "$tmp" &
pid=$!
trap 'kill "$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true; rm -rf "$tmp"' EXIT
for _ in $(seq 1 50); do curl -fs "http://127.0.0.1:8098/healthz" >/dev/null && break; sleep 0.2; done
curl -fs "http://127.0.0.1:8098/healthz" >/dev/null || { echo "exporter did not become ready" >&2; cat "$tmp/server.log" >&2; exit 1; }
scripts/exporter-contract-test.sh "http://127.0.0.1:8098" "$token"
trap - EXIT
kill "$pid" 2>/dev/null || true
wait "$pid" 2>/dev/null || true
rm -rf "$tmp"

if command -v gitleaks >/dev/null; then
  gitleaks git --no-banner --redact .
  gitleaks dir --no-banner --redact .
else
  echo "warning: gitleaks not installed (brew install gitleaks); secret scan skipped" >&2
fi

echo "check.sh passed in $(( $(date +%s) - start ))s"
