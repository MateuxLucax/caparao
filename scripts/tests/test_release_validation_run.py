"""Tests for selecting the exact successful PR validation run."""
from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location(
    "select_release_validation_run",
    Path(__file__).resolve().parents[1] / "ci" / "select-release-validation-run.py",
)
selector = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(selector)


def workflow_run(run_id: int, sha: str, created_at: str, conclusion: str, mutable_pr_sha: str) -> dict:
    return {
        "id": run_id,
        "head_sha": sha,
        "created_at": created_at,
        "conclusion": conclusion,
        "pull_requests": [
            {"number": 362, "head": {"sha": mutable_pr_sha}, "base": {"ref": "release"}}
        ],
    }


class ReleaseValidationRunTest(unittest.TestCase):
    def test_uses_run_sha_when_pr_metadata_has_moved(self) -> None:
        payload = {"workflow_runs": [workflow_run(1, "old", "2026-11-09T10:00:00Z", "success", "new")]}
        self.assertIsNone(selector.select_run_id(payload, 362, "new"))

    def test_selects_latest_success_for_exact_pr_and_head(self) -> None:
        payload = {
            "workflow_runs": [
                workflow_run(1, "old", "2026-11-09T10:00:00Z", "success", "new"),
                workflow_run(2, "new", "2026-11-09T11:00:00Z", "success", "new"),
                workflow_run(3, "new", "2026-11-09T09:00:00Z", "success", "new"),
                {**workflow_run(4, "new", "2026-11-09T12:00:00Z", "success", "new"),
                 "pull_requests": [{"number": 999, "base": {"ref": "release"}}]},
            ]
        }
        self.assertEqual(2, selector.select_run_id(payload, 362, "new"))

    def test_merged_pr_with_empty_metadata_keeps_exact_head_sha(self) -> None:
        run = workflow_run(5, "new", "2026-11-09T12:00:00Z", "success", "new")
        run["pull_requests"] = []
        self.assertEqual(5, selector.select_run_id({"workflow_runs": [run]}, 362, "new"))
        self.assertIsNone(selector.select_run_id({"workflow_runs": [run]}, 362, "other"))

    def test_latest_failed_rerun_fails_closed_despite_older_success(self) -> None:
        payload = {
            "workflow_runs": [
                workflow_run(1, "new", "2026-11-09T10:00:00Z", "success", "new"),
                workflow_run(2, "new", "2026-11-09T11:00:00Z", "failure", "new"),
            ]
        }
        self.assertIsNone(selector.select_run_id(payload, 362, "new"))


if __name__ == "__main__":
    unittest.main()
