#!/usr/bin/env bash
# Shared local and CI gate: JVM tests, lint, debug build, instrumented-test compilation and diagnostic script tests.
# Gitleaks is deliberately local-only; CI does not install it, while local runs use it when available.
set -euo pipefail
cd "$(dirname "$0")/.."

start=$(date +%s)
./gradlew --console=plain :app:testDebugUnitTest :wear:testDebugUnitTest :wearlink:test lintDebug assembleDebug compileDebugAndroidTestKotlin
python3 -m unittest discover -s scripts/tests -p 'test_*.py'

if command -v gitleaks >/dev/null; then
  gitleaks git --no-banner --redact .
  gitleaks dir --no-banner --redact .
else
  echo "warning: gitleaks not installed (brew install gitleaks); secret scan skipped" >&2
fi

echo "check.sh passed in $(( $(date +%s) - start ))s"
