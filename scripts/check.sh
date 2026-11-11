#!/usr/bin/env bash
# Shared local and CI gate: JVM tests, lint, debug build, instrumented-test compilation and diagnostic script tests.
# Gitleaks is deliberately local-only; CI does not install it, while local runs use it when available.
set -euo pipefail
cd "$(dirname "$0")/.."

start=$(date +%s)
# The benchmark modules are deliberately absent: they run on an emulator/device, never in the fast gate (#312).
# CI runs the suites below as parallel jobs; no argument (the default, and what local runs use) runs all of them.
suite="${1:-all}"
run_gradle() { ./gradlew --console=plain "$@"; }
run_scripts() { python3 -m unittest discover -s scripts/tests -p 'test_*.py'; }

case "$suite" in
  all)
    run_gradle :app:testDebugUnitTest :wear:testDebugUnitTest :wearlink:test \
      :app:lintDebug :wear:lintDebug \
      :app:assembleDebug :wear:assembleDebug \
      :app:compileDebugAndroidTestKotlin :wear:compileDebugAndroidTestKotlin
    run_scripts
    ;;
  tests) run_gradle :app:testDebugUnitTest :wear:testDebugUnitTest :wearlink:test ;;
  lint) run_gradle :app:lintDebug :wear:lintDebug ;;
  build)
    run_gradle :app:assembleDebug :wear:assembleDebug \
      :app:compileDebugAndroidTestKotlin :wear:compileDebugAndroidTestKotlin
    run_scripts
    ;;
  *) echo "usage: scripts/check.sh [all|tests|lint|build]" >&2; exit 2 ;;
esac

if [ "$suite" = all ] && command -v gitleaks >/dev/null; then
  gitleaks git --no-banner --redact .
  gitleaks dir --no-banner --redact .
elif [ "$suite" = all ]; then
  echo "warning: gitleaks not installed (brew install gitleaks); secret scan skipped" >&2
fi

echo "check.sh passed in $(( $(date +%s) - start ))s"
