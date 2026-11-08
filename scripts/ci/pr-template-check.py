#!/usr/bin/env python3
"""Fail when a pull request body does not follow .github/pull_request_template.md.

Reads the body from the PR_BODY environment variable (or a file given as the only argument) and prints one line per
problem. Exit 0 when the body follows the template, 1 otherwise.
"""

from __future__ import annotations

import os
import re
import sys
from pathlib import Path

TEMPLATE = Path(__file__).resolve().parents[2] / ".github" / "pull_request_template.md"
STATUSES = ("✅", "❌", "⏭️", "⏭")
CLOSING = re.compile(r"\b(close[sd]?|fix(e[sd])?|resolve[sd]?)\s+#\d+", re.IGNORECASE)
NOT_APPLICABLE = re.compile(r"\bN/A\b|\bNA\b")
LINK = re.compile(r"https?://\S+")
IMAGE = re.compile(r"!\[[^\]]*\]\([^)]+\)|<img\s[^>]*src=|<video\s|https://github\.com/user-attachments/", re.IGNORECASE)


def strip_comments(text: str) -> str:
    return re.sub(r"<!--.*?-->", "", text, flags=re.DOTALL)


def sections(body: str) -> dict[str, str]:
    """The `## ` sections of [body], ignoring heading-like lines inside fenced code blocks."""
    found: dict[str, str] = {}
    current = None
    lines: list[str] = []
    fenced = False
    for line in body.splitlines():
        if line.lstrip().startswith("```"):
            fenced = not fenced
        heading = None if fenced else re.fullmatch(r"##\s+(.+?)\s*#*\s*", line)
        if heading:
            if current is not None:
                found[current] = "\n".join(lines).strip()
            current, lines = heading.group(1).strip(), []
        elif current is not None:
            lines.append(line)
    if current is not None:
        found[current] = "\n".join(lines).strip()
    return found


def template_sections(path: Path = TEMPLATE) -> list[str]:
    """The section names the template asks for, in order: the single source of truth for the check."""
    return list(sections(strip_comments(path.read_text(encoding="utf-8"))))


def table_rows(text: str) -> list[list[str]]:
    rows = []
    for line in text.splitlines():
        line = line.strip()
        if not line.startswith("|"):
            continue
        cells = [cell.strip() for cell in line.strip("|").split("|")]
        if all(re.fullmatch(r":?-+:?", cell) for cell in cells if cell):
            continue
        rows.append(cells)
    return rows[1:]  # drop the header row


def check(body: str, required: list[str] | None = None) -> list[str]:
    required = template_sections() if required is None else required
    # The template's own placeholder is not content: an untouched template stays empty and unlinked.
    body = re.sub(r"(?m)^\s*Closes #<n>\s*$", "", strip_comments(body.replace("\r\n", "\n")))
    found = sections(body)
    problems = [f"Missing section: ## {name}" for name in required if name not in found]
    for name in required:
        if name in found and not found[name]:
            problems.append(f"Empty section: ## {name}")

    scenarios = table_rows(found.get("Test scenarios", ""))
    filled = [row for row in scenarios if len(row) >= 6 and all(row[1:5]) and row[3] != "1. …"]
    if found.get("Test scenarios") is not None and not filled:
        problems.append("## Test scenarios: fill at least one row (goal, preconditions, steps, expected result)")
    for row in filled:
        if not any(status in row[5] for status in STATUSES):
            problems.append(f"## Test scenarios: {row[0] or 'a row'} needs a status (✅, ❌ or ⏭️)")
        elif "⏭" in row[5] and len(re.sub(r"[⏭️\s]", "", row[5])) == 0:
            problems.append(f"## Test scenarios: {row[0] or 'a row'} is not run; say why")

    evidence = found.get("Evidence", "")
    blocks = re.findall(r"```[^\n]*\n(.*?)```", evidence, flags=re.DOTALL)
    outside = re.sub(r"```.*?```", "", evidence, flags=re.DOTALL).strip()
    if "Evidence" in found and not any(block.strip() for block in blocks) and not LINK.search(outside):
        problems.append("## Evidence: paste command output in a code block, or link CI runs or logs")

    shots = found.get("Screenshots", "")
    if "Screenshots" in found:
        if not NOT_APPLICABLE.search(shots) and not IMAGE.search(shots):
            problems.append("## Screenshots: add before/after screens, or write N/A when nothing is visible")

    scope = found.get("Out of scope", "")
    if "Out of scope" in found and not re.sub(r"^[-*]\s*$", "", scope, flags=re.MULTILINE).strip():
        problems.append("## Out of scope: list what was left out, or write None")

    if not CLOSING.search(body):
        problems.append("Link the issue this PR closes: Closes #<n>")
    return problems


def main(argv: list[str]) -> int:
    body = open(argv[1], encoding="utf-8").read() if len(argv) > 1 else os.environ.get("PR_BODY", "")
    problems = check(body)
    for problem in problems:
        print(problem)
    if problems:
        print("\nEdit the PR description to follow .github/pull_request_template.md; this check reruns on edit.")
        return 1
    print("PR description follows the template.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
