#!/usr/bin/env bash
# Pulls the stage 0 probe's session files from the phone into spike-captures/ (gitignored: raw captures are reviewed
# before anything is committed as a test fixture). See docs/spike.md.
set -euo pipefail
cd "$(dirname "$0")/.."

adb="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
[ -x "$adb" ] || adb=adb
remote=/sdcard/Android/data/io.github.mateuxlucax.caparao.probe/files/sessions

# With an emulator running next to the phone, adb can't pick a device: use the one physical device, unless
# ANDROID_SERIAL already names one.
if [ -z "${ANDROID_SERIAL:-}" ]; then
  phones=$("$adb" devices | awk 'NR > 1 && $2 == "device" && $1 !~ /^emulator-/ { print $1 }')
  if [ "$(printf '%s\n' "$phones" | grep -c .)" -eq 1 ]; then
    export ANDROID_SERIAL=$phones
  elif [ -n "$phones" ]; then
    echo "More than one phone connected; set ANDROID_SERIAL to one of:" >&2
    printf '%s\n' "$phones" >&2
    exit 1
  fi
fi

mkdir -p spike-captures
"$adb" pull "$remote/." spike-captures/
echo "pulled into spike-captures/:"
ls -l spike-captures
