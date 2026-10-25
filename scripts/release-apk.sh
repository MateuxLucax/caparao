#!/usr/bin/env bash
# Builds a release APK signed with the release key (created by scripts/release-key.sh), the same way the Release
# workflow does, without spending Actions minutes. With --install it also installs it on the one connected device.
#
#   scripts/release-apk.sh [--install] [keystore path]
set -euo pipefail
cd "$(dirname "$0")/.."

install=false
if [ "${1:-}" = "--install" ]; then install=true; shift; fi
export SIGNING_KEYSTORE_PATH="${1:-$HOME/.config/caparao/release.jks}"
[ -f "$SIGNING_KEYSTORE_PATH" ] || { echo "error: no keystore at $SIGNING_KEYSTORE_PATH (scripts/release-key.sh)" >&2; exit 1; }
SIGNING_STORE_PASSWORD="$(security find-generic-password -s "${CAPARAO_SIGNING_SERVICE:-caparao-signing}" -a release -w)"
export SIGNING_STORE_PASSWORD SIGNING_KEY_PASSWORD="$SIGNING_STORE_PASSWORD" SIGNING_KEY_ALIAS=release

version="$(tr -d '[:space:]' < VERSION)-local"
./gradlew --console=plain assembleRelease -PversionName="$version"
apk=app/build/outputs/apk/release/app-release.apk

apksigner="$(ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/*/ | sort -V | tail -n 1)apksigner"
"$apksigner" verify --print-certs "$apk" | grep -i -m 1 'certificate SHA-256 digest:'
echo "$apk"

if [ "$install" = true ]; then
  "${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb" install -r "$apk"
fi
