#!/usr/bin/env python3
"""Download a Sentry capture and validate it for ReplayScaleSource."""
import argparse
import gzip
import io
import json
import os
import re
import sys
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import quote, urlencode, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener

ROOT = Path(__file__).resolve().parents[2]
MAX_CAPTURE_BYTES = 16 * 1024 * 1024
EVENT_ID = re.compile(r"^[a-fA-F0-9]{32}$")


class SafeRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        redirected = super().redirect_request(req, fp, code, msg, headers, newurl)
        if urlsplit(newurl).scheme != "https":
            raise ValueError("Refusing non-HTTPS redirect")
        if redirected and urlsplit(newurl).netloc != urlsplit(req.full_url).netloc:
            redirected.remove_header("Authorization")
        return redirected


def api_get(url, token):
    if urlsplit(url).scheme != "https":
        raise ValueError("Sentry URL must use HTTPS")
    request = Request(url, headers={"Authorization": f"Bearer {token}", "Accept": "application/json"})
    with build_opener(SafeRedirect()).open(request, timeout=30) as response:
        data = response.read(MAX_CAPTURE_BYTES + 1)
        if len(data) > MAX_CAPTURE_BYTES:
            raise ValueError("Sentry response exceeds the size limit")
        return data


def convert_capture(data):
    """Validate the versioned diagnostic stream, retaining the original notification chunks."""
    with gzip.GzipFile(fileobj=io.BytesIO(data)) as compressed:
        raw = compressed.read(MAX_CAPTURE_BYTES + 1)
    if len(raw) > MAX_CAPTURE_BYTES:
        raise ValueError("Capture exceeds the size limit")
    lines = raw.decode("utf-8").splitlines()
    if len(lines) < 2:
        raise ValueError("Capture is empty")
    records = [json.loads(line) for line in lines]
    header = records[0]
    if header.get("format") != "caparao-diagnostic-jsonl" or header.get("format_version") != 1:
        raise ValueError("Unsupported capture format")
    previous_time = -1
    previous_sequence = -1
    for record in records[1:]:
        t = record.get("t_ms")
        seq = record.get("seq")
        if type(t) is not int or type(seq) is not int or t < previous_time or seq <= previous_sequence:
            raise ValueError("Capture timestamps or sequence are invalid")
        if record.get("dir") not in ("rx", "tx", "event") or not isinstance(record.get("note"), str):
            raise ValueError("Invalid capture record")
        hex_data = record.get("hex")
        if not isinstance(hex_data, str):
            raise ValueError("Invalid capture bytes")
        try:
            bytes.fromhex(hex_data)
        except ValueError as exc:
            raise ValueError("Invalid capture bytes") from exc
        previous_time, previous_sequence = t, seq
    footer = records[-1]
    if footer.get("note") not in ("session_end", "session_incomplete"):
        raise ValueError("Capture has no footer")
    return "\n".join(json.dumps(record, ensure_ascii=False, separators=(",", ":")) for record in records) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("event_id", help="Sentry event ID")
    parser.add_argument("--org", required=True)
    parser.add_argument("--project", required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    token = os.environ.get("SENTRY_AUTH_TOKEN")
    if not token:
        parser.error("SENTRY_AUTH_TOKEN is required")
    if not EVENT_ID.fullmatch(args.event_id):
        parser.error("event ID must be 32 hexadecimal characters")
    base = "https://sentry.io/api/0/projects/{}/{}/events/{}/attachments/".format(
        quote(args.org, safe=""), quote(args.project, safe=""), args.event_id
    )
    try:
        attachments = json.loads(api_get(base, token))
        captures = [a for a in attachments if a.get("name") == "capture.jsonl.gz"]
        if len(captures) != 1 or not str(captures[0].get("id", "")).isdigit():
            raise ValueError("Expected exactly one capture.jsonl.gz attachment")
        data = api_get(base + str(captures[0]["id"]) + "/?" + urlencode({"download": "1"}), token)
        result = convert_capture(data)
        output = args.output or ROOT / "spike-captures" / f"sentry-{args.event_id}.jsonl"
        output.parent.mkdir(parents=True, exist_ok=True)
        with output.open("x", encoding="utf-8") as file:
            file.write(result)
        print(output)
    except (OSError, ValueError, KeyError, json.JSONDecodeError, HTTPError) as exc:
        print(f"Capture download failed: {type(exc).__name__}: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
