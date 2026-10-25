#!/usr/bin/env bash
# Pulls the stage 0 probe's session files from the phone into spike-captures/ (gitignored: raw captures are reviewed
# before anything is committed as a test fixture). See docs/spike.md.
set -euo pipefail
cd "$(dirname "$0")/.."

adb="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
[ -x "$adb" ] || adb=adb
remote=/sdcard/Android/data/io.github.mateuxlucax.caparao.probe/files/sessions

mkdir -p spike-captures
"$adb" pull "$remote/." spike-captures/
echo "pulled into spike-captures/:"
ls -l spike-captures
