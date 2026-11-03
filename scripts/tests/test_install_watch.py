import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "install-watch.sh"


class InstallWatchTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.bin = Path(self.temp.name)
        self.log = self.bin / "calls"
        gh = self.bin / "gh"
        gh.write_text('''#!/usr/bin/env python3
import json, os, pathlib, sys
args=sys.argv[1:]
if args[:2] == ["release", "view"]:
 print(json.dumps({"tagName":"v0.3.57", "assets":[{"name":os.environ.get("ASSET", "caparao-wear-0.3.57.apk") }]})); sys.exit(0)
if args[:2] == ["release", "download"]:
 pathlib.Path(args[args.index("--dir")+1], args[args.index("--pattern")+1]).write_text("apk"); sys.exit(int(os.environ.get("DOWNLOAD_FAIL", "0")))
sys.exit(3)
''')
        gh.chmod(0o755)
        adb = self.bin / "adb"
        adb.write_text('''#!/bin/sh
printf '%s\\n' "$*" >> "$CALL_LOG"
case "$1" in
 connect) echo "${CONNECT_OUTPUT:-connected to $2}"; exit "${CONNECT_EXIT:-0}";;
 -s) [ "$3" = get-state ] && { echo "${STATE:-device}"; exit "${STATE_EXIT:-0}"; }; [ "$3" = install ] && exit "${INSTALL_EXIT:-0}";;
esac
''')
        adb.chmod(0o755)
        self.env = {**os.environ, "PATH": f"{self.bin}:{os.environ['PATH']}", "CALL_LOG": str(self.log)}

    def tearDown(self):
        self.temp.cleanup()

    def run_script(self, *args, **env):
        return subprocess.run([str(SCRIPT), *args], env={**self.env, **env}, text=True, capture_output=True)

    def test_pins_wear_asset_and_exact_target(self):
        result = self.run_script("192.0.2.4", "5555")
        self.assertEqual(result.returncode, 0, result.stderr)
        calls = self.log.read_text().splitlines()
        self.assertEqual(calls[0], "connect 192.0.2.4:5555")
        self.assertEqual(calls[1], "-s 192.0.2.4:5555 get-state")
        self.assertIn("-s 192.0.2.4:5555 install -r", calls[2])
        self.assertIn("caparao-wear-0.3.57.apk", calls[2])

    def test_unusable_target_and_install_failure(self):
        self.assertNotEqual(self.run_script("192.0.2.4:5555", STATE="unauthorized").returncode, 0)
        self.assertNotEqual(self.run_script("192.0.2.4:5555", INSTALL_EXIT="1").returncode, 0)

    def test_bad_args_and_missing_asset(self):
        self.assertEqual(self.run_script("noport").returncode, 2)
        self.assertNotEqual(self.run_script("192.0.2.4:5555", ASSET="caparao-0.3.57.apk").returncode, 0)


if __name__ == "__main__":
    unittest.main()
