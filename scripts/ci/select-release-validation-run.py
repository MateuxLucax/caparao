#!/usr/bin/env python3
"""Select the latest successful release validation run for one PR head SHA."""

from __future__ import annotations

import json
import sys
from typing import Any


def select_run_id(payload: dict[str, Any], pull_request: int, head_sha: str) -> int | None:
    runs = [
        run
        for run in payload.get("workflow_runs", [])
        if run.get("head_sha") == head_sha
        and any(
            item.get("number") == pull_request and item.get("base", {}).get("ref") == "release"
            for item in run.get("pull_requests", [])
        )
    ]
    latest = max(runs, key=lambda run: run.get("created_at", ""), default=None)
    return latest.get("id") if latest and latest.get("conclusion") == "success" else None


def main() -> int:
    pull_request, head_sha = int(sys.argv[1]), sys.argv[2]
    run_id = select_run_id(json.load(sys.stdin), pull_request, head_sha)
    if run_id is None:
        print(f"No successful release validation run for PR #{pull_request} at {head_sha}.", file=sys.stderr)
        return 1
    print(run_id)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
