#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'HELP'
Install or update the Caparaó Wear OS app from the latest GitHub release.

Usage: scripts/install-watch.sh <watch-ip:connection-port>
       scripts/install-watch.sh <watch-ip> <connection-port>

The watch must be on Wi-Fi with Developer options and Wireless debugging enabled.
For first-time setup, pair in Developer options > Wireless debugging, then run:
  adb pair <watch-ip>:<pairing-port>
Enter the pairing code when prompted. Use the separate connection port shown on
that screen with this script. Pairing codes are never requested by this script.
HELP
}
fail() { echo "error: $*" >&2; exit 1; }
if [[ "${1:-}" == -h || "${1:-}" == --help ]]; then usage; exit 0; fi
if [[ $# -eq 1 && "$1" == *:* ]]; then endpoint=$1; ip=${endpoint%:*}; port=${endpoint##*:}
elif [[ $# -eq 2 ]]; then ip=$1; port=$2; endpoint="$ip:$port"
else usage >&2; exit 2; fi
[[ -n "$ip" && "$port" =~ ^[0-9]+$ ]] || fail 'provide a watch IP and numeric connection port'
(( port >= 1 && port <= 65535 )) || fail 'port must be between 1 and 65535'

command -v gh >/dev/null || fail 'GitHub CLI (gh) is required; install and authenticate it first'
adb=${ADB:-}
if [[ -z "$adb" ]]; then
  if command -v adb >/dev/null; then adb=$(command -v adb)
  else
    sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}
    [[ -x "$sdk/platform-tools/adb" ]] || fail 'adb not found; add platform-tools to PATH or set ANDROID_HOME/ADB'
    adb="$sdk/platform-tools/adb"
  fi
fi
[[ -x "$adb" ]] || { command -v "$adb" >/dev/null 2>&1 || fail "adb is not executable: $adb"; }
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
release=$(gh release view --repo MateuxLucax/caparao --json tagName,assets) || fail 'could not read latest published release'
tag=$(python3 -c 'import json,sys; print(json.load(sys.stdin).get("tagName", ""))' <<<"$release")
[[ -n "$tag" ]] || fail 'latest release has no tag'
version=${tag#v}
asset="caparao-wear-$version.apk"
count=$(python3 -c 'import json,sys; a=json.load(sys.stdin).get("assets", []); print(sum(x.get("name")==sys.argv[1] for x in a))' "$asset" <<<"$release")
[[ "$count" == 1 ]] || fail "expected exactly one $asset asset in latest release $tag"
apk="$tmp/$asset"
gh release download "$tag" --repo MateuxLucax/caparao --pattern "$asset" --dir "$tmp" || fail "failed downloading $asset"
[[ -s "$apk" ]] || fail "download did not produce $asset"

connect_output=$("$adb" connect "$endpoint" 2>&1) || fail "adb connect failed for $endpoint: $connect_output"
case "$connect_output" in *"connected to $endpoint"*|*"already connected to $endpoint"*) ;; *) fail "adb did not confirm connection to $endpoint: $connect_output";; esac
state=$("$adb" -s "$endpoint" get-state 2>&1) || fail "watch $endpoint is not usable: $state"
[[ "$state" == device ]] || fail "watch $endpoint is not usable (ADB state: $state)"
"$adb" -s "$endpoint" install -r "$apk" || fail "installation failed for $endpoint; existing app data was preserved"
echo "Installed $asset on $endpoint (existing app data preserved)."
