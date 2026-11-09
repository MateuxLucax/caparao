#!/usr/bin/env bash
# Shared local and CI gate: JVM tests, lint, debug build, instrumented-test compilation and diagnostic script tests.
# Gitleaks is deliberately local-only; CI does not install it, while local runs use it when available.
set -euo pipefail
cd "$(dirname "$0")/.."

start=$(date +%s)
# The benchmark modules are deliberately absent: they run on an emulator/device, never in the fast gate (#312).
./gradlew --console=plain :app:testDebugUnitTest :wear:testDebugUnitTest :wearlink:test \
  :app:lintDebug :wear:lintDebug \
  :app:assembleDebug :wear:assembleDebug \
  :app:compileDebugAndroidTestKotlin :wear:compileDebugAndroidTestKotlin
python3 -m unittest discover -s scripts/tests -p 'test_*.py'

if command -v gitleaks >/dev/null; then
  gitleaks git --no-banner --redact .
  gitleaks dir --no-banner --redact .
else
  echo "warning: gitleaks not installed (brew install gitleaks); secret scan skipped" >&2
fi

echo "check.sh passed in $(( $(date +%s) - start ))s"
