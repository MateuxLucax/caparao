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
import sys, urllib.request, urllib.error, json, pathlib

base, token = sys.argv[1], sys.argv[2]

def post(body, headers=None, raw=False):
    hdrs = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    if headers is not None:
        hdrs = headers
    req = urllib.request.Request(base + "/api/v1/brews", data=body, headers=hdrs, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status, resp.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()

valid = sorted(pathlib.Path("contract/valid").glob("*.json"))
invalid = sorted(pathlib.Path("contract/invalid").glob("*.json"))
if not valid or not invalid:
    print("no contract fixtures found", file=sys.stderr)
    sys.exit(1)
for path in valid:
    first, _ = post(path.read_bytes(), raw=True)
    if first != 201:
        print(f"contract {path}: first POST got {first}, want 201", file=sys.stderr)
        sys.exit(1)
    resend, _ = post(path.read_bytes(), raw=True)
    if resend != 200:
        print(f"contract {path}: resend got {resend}, want 200 (upsert)", file=sys.stderr)
        sys.exit(1)
for path in invalid:
    status, body = post(path.read_bytes(), raw=True)
    if status != 400:
        print(f"contract {path}: POST got {status}, want 400", file=sys.stderr)
        sys.exit(1)
    try:
        if "error" not in json.loads(body or b"{}"):
            print(f"contract {path}: 400 body lacks error field", file=sys.stderr)
            sys.exit(1)
    except json.JSONDecodeError:
        print(f"contract {path}: 400 body is not JSON", file=sys.stderr)
        sys.exit(1)

# Status-code coverage against the same exporter (#166): unauthenticated,
# wrong-method and oversized payloads. Anonymous is checked without the
# Authorization header; the oversized body only needs to be large enough
# that both the app client and the exporter reject it.
def raw_request(path, method, headers=None, body=None):
    req = urllib.request.Request(base + path, data=body, headers=headers or {}, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status
    except urllib.error.HTTPError as error:
        return error.code

anon = raw_request("/api/v1/brews", "POST", {"Content-Type": "application/json"}, b"{}")
if anon != 401:
    print(f"contract: anonymous POST got {anon}, want 401", file=sys.stderr)
    sys.exit(1)
wrong_method = raw_request("/api/v1/brews", "PUT", {"Authorization": f"Bearer {token}"})
if wrong_method != 405:
    print(f"contract: PUT got {wrong_method}, want 405", file=sys.stderr)
    sys.exit(1)
big_body = json.dumps({"id": "x"}).encode() + b" " * (5 * 1024 * 1024)
too_large = raw_request("/api/v1/brews", "POST", {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}, big_body)
if too_large != 413:
    print(f"contract: oversized POST got {too_large}, want 413", file=sys.stderr)
    sys.exit(1)

print(f"contract: {len(valid)} valid accepted (201 then 200), {len(invalid)} invalid rejected (400), 401, 405, 413")

PY
