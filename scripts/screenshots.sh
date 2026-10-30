#!/usr/bin/env bash
# Refreshes docs/screenshots on the JVM, no emulator: ScreenshotsTest renders the onboarding, every tab and the brew
# flows with Robolectric and Roborazzi, in English and Portuguese, light and dark, with the recorded sessions played
# frame by frame so the images only change when a screen does.
#   ONLY=Espresso scripts/screenshots.sh     one test (onboardingAndEveryTab, espresso, espressoGuide, filter)
#   VARIANTS=EnLight scripts/screenshots.sh  one language and theme (EnLight, EnDark, PtBrLight, PtBrDark)
set -euo pipefail
cd "$(dirname "$0")/.."

filter="io.github.mateuxlucax.caparao.ui.screens.Screenshots${VARIANTS:-*}Test${ONLY:+.$ONLY}"
./gradlew --console=plain -q :app:recordRoborazziDebug --tests "$filter"
echo "Screenshots in docs/screenshots/"
