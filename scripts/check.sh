#!/usr/bin/env bash
# Local gate before pushing: JVM unit tests, Android lint, debug build, a compile of the instrumented tests and a
# secret scan. Mirrors the CI job, so a green run here should be a green run there (docs/adr/0015-acceptance-and-lean-ci.md).
set -euo pipefail
cd "$(dirname "$0")/.."

start=$(date +%s)
./gradlew --console=plain testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin

if command -v gitleaks >/dev/null; then
  gitleaks git --no-banner --redact .
  gitleaks dir --no-banner --redact .
else
  echo "warning: gitleaks not installed (brew install gitleaks); secret scan skipped" >&2
fi

echo "check.sh passed in $(( $(date +%s) - start ))s"
