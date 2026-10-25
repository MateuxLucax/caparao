#!/usr/bin/env bash
# Creates the release signing key once, outside the repository, and optionally uploads it as the four repository
# secrets the Release workflow needs (README, Release signing). Every release must be signed with this same key, or
# updates won't install over the previous version, so back up the keystore and its password.
#
#   scripts/release-key.sh [keystore path]            create the key (default ~/.config/caparao/release.jks)
#   scripts/release-key.sh --upload [keystore path]   upload an existing key to the GitHub repository secrets
#
# The password is random, stored in the macOS login Keychain (service caparao-signing) and never printed. The
# certificate holds no personal data (CN=Caparao), since it ships inside every public APK.
set -euo pipefail

service="${CAPARAO_SIGNING_SERVICE:-caparao-signing}"
alias=release
upload=false
if [ "${1:-}" = "--upload" ]; then upload=true; shift; fi
keystore="${1:-$HOME/.config/caparao/release.jks}"

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
case "$(cd "$(dirname "$keystore")" 2>/dev/null && pwd)/" in
  "$repo_root"/*) echo "error: keep the keystore outside the repository" >&2; exit 1 ;;
esac

if [ "$upload" = false ]; then
  if [ -e "$keystore" ]; then
    echo "error: $keystore already exists; a new key would break updates for every installed copy" >&2
    exit 1
  fi
  if security find-generic-password -s "$service" -a "$alias" >/dev/null 2>&1; then
    echo "error: the Keychain already has a $service password; delete it first if the old key is really gone" >&2
    exit 1
  fi
  mkdir -p "$(dirname "$keystore")"
  chmod 700 "$(dirname "$keystore")"
  SIGNING_PASSWORD="$(openssl rand -hex 32)"
  export SIGNING_PASSWORD
  # PKCS12 keystores use one password for the store and the key.
  keytool -genkeypair -keystore "$keystore" -storetype PKCS12 -alias "$alias" \
    -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Caparao" \
    -storepass:env SIGNING_PASSWORD -keypass:env SIGNING_PASSWORD >/dev/null 2>&1
  chmod 600 "$keystore"
  security add-generic-password -s "$service" -a "$alias" -l "Caparaó release key ($keystore)" -w "$SIGNING_PASSWORD"
  echo "Created $keystore; its password is in the Keychain item \"$service\"."
else
  [ -f "$keystore" ] || { echo "error: no keystore at $keystore" >&2; exit 1; }
  SIGNING_PASSWORD="$(security find-generic-password -s "$service" -a "$alias" -w)"
  export SIGNING_PASSWORD
  base64 -i "$keystore" | tr -d '\n' | gh secret set SIGNING_KEYSTORE_BASE64
  printf %s "$SIGNING_PASSWORD" | gh secret set SIGNING_STORE_PASSWORD
  printf %s "$SIGNING_PASSWORD" | gh secret set SIGNING_KEY_PASSWORD
  printf %s "$alias" | gh secret set SIGNING_KEY_ALIAS
  echo "Uploaded the four signing secrets to $(gh repo view --json nameWithOwner -q .nameWithOwner)."
fi

echo "Signer certificate SHA-256 (published with each release):"
keytool -list -v -keystore "$keystore" -alias "$alias" -storepass:env SIGNING_PASSWORD | grep -m 1 "SHA256:" | sed 's/.*SHA256: *//'
[ "$upload" = true ] || echo "Back up the keystore file and the Keychain password (e.g. in a password manager)."
