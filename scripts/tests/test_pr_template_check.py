"""Tests for scripts/ci/pr-template-check.py: the PR description must follow .github/pull_request_template.md."""
from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("pr_template_check", REPO_ROOT / "scripts" / "ci" / "pr-template-check.py")
checker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(checker)

FILLED = """## Objective

Show one clock on the French press steep screen.

## Description

The press art moves above the ring. Closes #302

## Test scenarios

<!-- One row per scenario. -->

| ID | Goal | Preconditions | Steps | Expected result | Status |
|----|------|---------------|-------|-----------------|--------|
| TS-01 | One clock | Steep running | 1. Reach the steep | Only the countdown | ✅ |

## Evidence

```text
check.sh passed in 203s
```

## Screenshots

| Before | After |
|--------|-------|
| <img src="https://github.com/user-attachments/assets/a" /> | <img src="https://github.com/user-attachments/assets/b" /> |

## Out of scope

- The deadline screen: unchanged by design.
"""


class PrTemplateCheckTest(unittest.TestCase):
    def test_filled_body_passes(self) -> None:
        self.assertEqual([], checker.check(FILLED))

    def test_untouched_template_fails_every_section(self) -> None:
        template = (REPO_ROOT / ".github" / "pull_request_template.md").read_text(encoding="utf-8")
        problems = checker.check(template)
        joined = "\n".join(problems)
        for name in ("Objective", "Description", "Test scenarios", "Evidence", "Screenshots", "Out of scope"):
            self.assertIn(name, joined)
        self.assertIn("Closes #<n>", joined)

    def test_free_text_body_fails(self) -> None:
        problems = checker.check("Moved the art.\n\nCloses #302")
        self.assertIn("Missing section: ## Screenshots", problems)

    def test_screenshots_accept_not_applicable(self) -> None:
        body = FILLED.replace('| <img src="https://github.com/user-attachments/assets/a" /> | <img src="https://github.com/user-attachments/assets/b" /> |', "| N/A | N/A |")
        self.assertEqual([], checker.check(body))

    def test_screenshots_need_an_image_or_not_applicable(self) -> None:
        body = FILLED.replace('| <img src="https://github.com/user-attachments/assets/a" /> | <img src="https://github.com/user-attachments/assets/b" /> |', "| | |")
        self.assertTrue(any("Screenshots" in p for p in checker.check(body)))

    def test_skipped_scenario_needs_a_reason(self) -> None:
        self.assertTrue(any("say why" in p for p in checker.check(FILLED.replace("| ✅ |", "| ⏭️ |"))))
        self.assertEqual([], checker.check(FILLED.replace("| ✅ |", "| ⏭️ needs a real Dot |")))

    def test_empty_evidence_block_fails(self) -> None:
        body = FILLED.replace("check.sh passed in 203s\n", "")
        self.assertTrue(any("Evidence" in p for p in checker.check(body)))

    def test_bare_out_of_scope_bullet_fails(self) -> None:
        body = FILLED.replace("- The deadline screen: unchanged by design.", "- ")
        self.assertTrue(any("Out of scope" in p for p in checker.check(body)))

    def test_closing_keyword_is_required(self) -> None:
        self.assertIn("Link the issue this PR closes: Closes #<n>", checker.check(FILLED.replace("Closes #302", "See #302")))

    def test_comments_do_not_count_as_content(self) -> None:
        body = FILLED.replace("check.sh passed in 203s\n", "").replace("```text\n```", "<!-- logs here -->")
        self.assertTrue(any("Evidence" in p for p in checker.check(body)))


if __name__ == "__main__":
    unittest.main()
