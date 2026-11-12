"""Tests for safe, repeatable GitHub Release publication."""
from __future__ import annotations

import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "ci" / "publish-release.sh"
FAKE_GH = r'''#!/usr/bin/env python3
import json
import os
import sys

args = sys.argv[1:]
with open(os.environ["GH_CALLS"], "a", encoding="utf-8") as log:
    log.write(json.dumps(args) + "\n")
state = json.loads(os.environ["GH_STATE"])
if args[0] == "api":
    if state.get("tag_sha") is None:
        print(json.dumps({"message": "No commit found for SHA: v0.3.42", "status": "422"}))
        raise SystemExit(1)
    print(state["tag_sha"])
elif args[:2] == ["release", "view"] and "--json" in args:
    if not state.get("release"):
        raise SystemExit(1)
    print(json.dumps({"isDraft": state.get("draft", False), "body": state.get("notes", "")}))
elif args[:2] == ["release", "view"]:
    raise SystemExit(0 if state.get("release") else 1)
'''


class PublishReleaseTest(unittest.TestCase):
    def publish(self, state: dict) -> tuple[subprocess.CompletedProcess, list[list[str]]]:
        with tempfile.TemporaryDirectory() as tmp:
            bin_dir = Path(tmp) / "bin"
            bin_dir.mkdir()
            fake_gh = bin_dir / "gh"
            fake_gh.write_text(FAKE_GH, encoding="utf-8")
            fake_gh.chmod(0o755)
            calls_file = Path(tmp) / "calls.jsonl"
            env = os.environ | {
                "PATH": f"{bin_dir}{os.pathsep}{os.environ['PATH']}",
                "GH_CALLS": str(calls_file),
                "GH_STATE": json.dumps(state),
                "GH_REPO": "MateuxLucax/caparao",
                "TAG": "v0.3.42",
                "TARGET_SHA": "target-sha",
                "APK": "caparao-0.3.42.apk",
                "WEAR_APK": "caparao-wear-0.3.42.apk",
                "AAB": "caparao-0.3.42.aab",
                "WEAR_AAB": "caparao-wear-0.3.42.aab",
                "SIGNER_NOTE": "APK signer certificate SHA-256: `abc123`",
            }
            result = subprocess.run([str(SCRIPT)], env=env, text=True, capture_output=True, check=False)
            calls = [json.loads(line) for line in calls_file.read_text(encoding="utf-8").splitlines()]
            return result, calls

    def test_partial_release_rerun_replaces_assets_and_restores_signer_note(self) -> None:
        result, calls = self.publish({"tag_sha": "target-sha", "release": True, "draft": False, "notes": "Release notes"})
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn(["release", "upload", "v0.3.42", "caparao-0.3.42.apk", "caparao-wear-0.3.42.apk", "caparao-0.3.42.aab", "caparao-wear-0.3.42.aab", "--clobber"], calls)
        self.assertTrue(any(call[:3] == ["release", "edit", "v0.3.42"] for call in calls))
        self.assertFalse(any(call[:2] == ["release", "create"] for call in calls))

    def test_partial_draft_release_is_published_after_assets_and_notes(self) -> None:
        result, calls = self.publish({"tag_sha": "target-sha", "release": True, "draft": True, "notes": "Release notes"})
        self.assertEqual(0, result.returncode, result.stderr)
        upload = calls.index(["release", "upload", "v0.3.42", "caparao-0.3.42.apk", "caparao-wear-0.3.42.apk", "caparao-0.3.42.aab", "caparao-wear-0.3.42.aab", "--clobber"])
        note = next(i for i, call in enumerate(calls) if call[:3] == ["release", "edit", "v0.3.42"] and "--notes" in call)
        publish = calls.index(["release", "edit", "v0.3.42", "--draft=false"])
        self.assertLess(upload, note)
        self.assertLess(note, publish)

    def test_existing_release_at_wrong_commit_is_not_modified(self) -> None:
        result, calls = self.publish({"tag_sha": "other-sha", "release": True, "notes": "Release notes"})
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(any(call[:2] in (["release", "upload"], ["release", "edit"], ["release", "create"]) for call in calls))

    def test_conflicting_tag_without_release_is_not_reused(self) -> None:
        result, calls = self.publish({"tag_sha": "other-sha", "release": False})
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(any(call[:2] == ["release", "create"] for call in calls))

    def test_matching_or_missing_tag_can_create_a_release(self) -> None:
        for tag_sha in ("target-sha", None):
            with self.subTest(tag_sha=tag_sha):
                result, calls = self.publish({"tag_sha": tag_sha, "release": False})
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertTrue(any(call[:2] == ["release", "create"] for call in calls))


if __name__ == "__main__":
    unittest.main()
