"""Keep the CI matrix and the full local gate in sync."""

import pathlib
import re
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]


class CheckSuitesTest(unittest.TestCase):
    def test_matrix_covers_all_gradle_tasks(self):
        script = (ROOT / "scripts/check.sh").read_text()
        workflow = (ROOT / ".github/workflows/build.yml").read_text()
        suites = re.search(r"suite: \[([^]]+)\]", workflow).group(1).replace(" ", "").split(",")
        branches = {
            name: body
            for name, body in re.findall(
                r"(?ms)^  (all|tests|lint|build)\)(.*?)(?=^  (?:all|tests|lint|build|\*)\)|\Z)",
                script,
            )
        }
        tasks = lambda branch: set(re.findall(r":[\w]+(?::[\w]+)+", branches[branch]))
        self.assertEqual({"tests", "lint", "build"}, set(suites))
        self.assertEqual(tasks("all"), set().union(*(tasks(suite) for suite in suites)))


if __name__ == "__main__":
    unittest.main()
