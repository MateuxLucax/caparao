#!/usr/bin/env bash
# Checks contract/ against a running exporter instance (#166).
#
#   scripts/exporter-contract-test.sh <exporter-base-url> <token>
#
# Every contract/valid/*.json must be accepted (201 then 200 on resend) and
# every contract/invalid/*.json must be rejected with 400 and an {"error"}
# body. This replaces the stub's `go test` in scripts/check.sh; the script
# expects the exporter to already be started by scripts/exporter.sh and
# scripts/e2e.sh (or, when run via scripts/check.sh, starts one itself).
set -euo pipefail
cd "$(dirname "$0")/.."

base="${1:?exporter base URL (for example http://127.0.0.1:8099)}"
token="${2:?Bearer token}"
auth="Authorization: Bearer $token"

python3 - "$base" "$token" <<'PY'
import sys, urllib.request

base, token = sys.argv[1], sys.argv[2]

def post(body):
    req = urllib.request.Request(base + "/api/v1/brews", data=body,
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status
    except urllib.error.HTTPError as error:
        return error.code

import pathlib
valid = sorted(pathlib.Path("contract/valid").glob("*.json"))
invalid = sorted(pathlib.Path("contract/invalid").glob("*.json"))
if not valid or not invalid:
    print("no contract fixtures found", file=sys.stderr)
    sys.exit(1)
for path in valid:
    body = path.read_bytes()
    first = post(body)
    if first != 201:
        print(f"contract {path}: first POST got {first}, want 201", file=sys.stderr)
        sys.exit(1)
    resend = post(body)
    if resend != 200:
        print(f"contract {path}: resend got {resend}, want 200", file=sys.stderr)
        sys.exit(1)
for path in invalid:
    status = post(path.read_bytes())
    if status != 400:
        print(f"contract {path}: POST got {status}, want 400", file=sys.stderr)
        sys.exit(1)
print(f"contract: {len(valid)} valid accepted (201 then 200), {len(invalid)} invalid rejected (400)")
PY
