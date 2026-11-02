"""Focused tests for scripts/e2e-diagnostics.py and the E2E diagnostic wrappers.

Every device identifier here is fake. A stub adb records its argv and emits a multi-device inventory plus
logcat lines containing fake serials, model names, a locally administered MAC address and a fake token.
The tests verify the AndroidRuntime filter, line/byte bounds, sanitization of every visible device and both
the success and failure paths of scripts/e2e-ci.sh without a real emulator or phone.
"""
from __future__ import annotations

import importlib.util
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = REPO_ROOT / "scripts" / "e2e-diagnostics.py"
SPEC = importlib.util.spec_from_file_location("e2e_diagnostics", SCRIPT)
diagnostics = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = diagnostics
SPEC.loader.exec_module(diagnostics)

EMULATOR_SERIAL = "emulator-5554"
OTHER_EMULATOR_SERIAL = "emulator-5556"
PHONE_SERIAL = "FAKEserial0001"
OTHER_PHONE_SERIAL = "FAKEserial0002"
EMULATOR_MODEL = "sdk_fake_x86_64"
EMULATOR_DEVICE = "fake_emu64"
PHONE_MODEL = "FAKE_MODEL_X"
PHONE_DEVICE = "fake_device_x"
PRODUCT = "fake_product"
MAC = "02:00:00:00:00:01"
TOKEN = "fake-test-token-not-a-secret"

DEVICES_OUTPUT = f"""List of devices attached
{EMULATOR_SERIAL}          device product:{EMULATOR_MODEL} model:{EMULATOR_MODEL} device:{EMULATOR_DEVICE} transport_id:1 usb:1-1
{PHONE_SERIAL}             device product:{PRODUCT} model:{PHONE_MODEL} device:{PHONE_DEVICE} transport_id:2
{OTHER_EMULATOR_SERIAL}    offline product:{EMULATOR_MODEL} model:{EMULATOR_MODEL} device:{EMULATOR_DEVICE} transport_id:3
{OTHER_PHONE_SERIAL}       unauthorized product:{PRODUCT} model:{PHONE_MODEL} device:{PHONE_DEVICE} transport_id:4
"""

LOGCAT_OUTPUT = f"""11-02 12:00:00.000 100 101 E AndroidRuntime: crash on {EMULATOR_SERIAL}
11-02 12:00:00.001 100 102 W OtherTag: this must stay silenced
11-02 12:00:00.002 100 103 E AndroidRuntime: phone {PHONE_SERIAL} model {PHONE_MODEL} device {PHONE_DEVICE}
11-02 12:00:00.003 100 104 E AndroidRuntime: mac {MAC} token={TOKEN}
"""

RAW_IDENTIFIERS = [
    EMULATOR_SERIAL,
    OTHER_EMULATOR_SERIAL,
    PHONE_SERIAL,
    OTHER_PHONE_SERIAL,
    EMULATOR_MODEL,
    EMULATOR_DEVICE,
    PHONE_MODEL,
    PHONE_DEVICE,
    PRODUCT,
    MAC,
    TOKEN,
    "transport_id:1",
    "transport_id:2",
    "transport_id:3",
    "transport_id:4",
    "usb:1-1",
]

FAKE_ADB = r'''#!/usr/bin/env python3
import json
import os
import sys
import time

argv = sys.argv[1:]
args_log = os.environ.get("FAKE_ADB_ARGS_LOG")
if args_log:
    with open(args_log, "a", encoding="utf-8") as handle:
        handle.write(json.dumps(argv) + "\n")

if "devices" in argv:
    sys.stdout.write(os.environ.get("FAKE_ADB_DEVICES", ""))
    sys.stdout.flush()
    raise SystemExit(int(os.environ.get("FAKE_ADB_DEVICES_STATUS", "0")))

if "logcat" in argv:
    time.sleep(float(os.environ.get("FAKE_ADB_LOGCAT_SLEEP", "0")))
    line_count = int(os.environ.get("FAKE_ADB_LOGCAT_LINES", "0"))
    if line_count:
        body = "".join(
            "11-02 12:00:00.000 123 456 E AndroidRuntime: fake failure %d\n" % index
            for index in range(line_count)
        )
    else:
        body = os.environ.get("FAKE_ADB_LOGCAT", "")
    if os.environ.get("FAKE_ADB_FILTER_AWARE") == "1":
        body = "".join(line + "\n" for line in body.splitlines() if "AndroidRuntime" in line)
    sys.stdout.write(body)
    sys.stdout.flush()
    raise SystemExit(int(os.environ.get("FAKE_ADB_LOGCAT_STATUS", "0")))

sys.stderr.write("fake adb: unsupported command\n")
raise SystemExit(2)
'''

FAKE_E2E = """#!/usr/bin/env bash
set -euo pipefail
printf 'e2e stdout serial=%s model=%s device=%s mac=%s token=%s\\n' \\
  "emulator-5554" "FAKE_MODEL_X" "fake_device_x" "02:00:00:00:00:01" "fake-test-token-not-a-secret"
printf 'server serial=%s\\n' "FAKEserial0001" > "$E2E_LOG_DIR/server.log"
printf 'instrumentation emulator=%s\\n' "emulator-5556" > "$E2E_LOG_DIR/instrumentation.log"
exit "${FAKE_E2E_STATUS:-0}"
"""


class E2EDiagnosticsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = Path(tempfile.mkdtemp(prefix="caparao-e2e-diagnostics-"))
        self.addCleanup(shutil.rmtree, self.temp_dir, ignore_errors=True)
        self.log_dir = self.temp_dir / "logs"
        self.log_dir.mkdir()
        self.adb_log = self.temp_dir / "adb-argv.jsonl"
        self.adb = self._write_fake_adb(self.temp_dir / "sdk")

    def _write_fake_adb(self, sdk_root: Path) -> Path:
        platform_tools = sdk_root / "platform-tools"
        platform_tools.mkdir(parents=True)
        adb = platform_tools / "adb"
        adb.write_text(FAKE_ADB, encoding="utf-8")
        adb.chmod(0o755)
        return adb

    def environment(self, **overrides: str) -> dict:
        env = os.environ.copy()
        env.pop("ANDROID_SERIAL", None)
        env.pop("E2E_FORCE_FAILURE", None)
        env.update(
            {
                "FAKE_ADB_ARGS_LOG": str(self.adb_log),
                "FAKE_ADB_DEVICES": DEVICES_OUTPUT,
                "FAKE_ADB_LOGCAT": LOGCAT_OUTPUT,
                "FAKE_ADB_FILTER_AWARE": "1",
            }
        )
        env.update(overrides)
        return env

    def collect(self, *extra: str, env: dict = None) -> subprocess.CompletedProcess:
        command = [
            sys.executable,
            str(SCRIPT),
            "collect",
            "--log-dir",
            str(self.log_dir),
            "--adb",
            str(self.adb),
        ]
        command.extend(extra)
        return subprocess.run(
            command,
            cwd=str(REPO_ROOT),
            env=env or self.environment(),
            capture_output=True,
            text=True,
            timeout=30,
        )

    def read_log(self, name: str) -> str:
        return (self.log_dir / name).read_text(encoding="utf-8")

    def adb_argv(self) -> list:
        if not self.adb_log.exists():
            return []
        return [
            json.loads(line)
            for line in self.adb_log.read_text(encoding="utf-8").splitlines()
            if line.strip()
        ]

    def assert_sanitized(self, text: str) -> None:
        for value in RAW_IDENTIFIERS:
            self.assertNotIn(value, text)

    def test_parse_inventory_classifies_every_visible_device(self) -> None:
        inventory = diagnostics.parse_inventory(DEVICES_OUTPUT)

        self.assertEqual(
            [EMULATOR_SERIAL, PHONE_SERIAL, OTHER_EMULATOR_SERIAL, OTHER_PHONE_SERIAL],
            [entry.serial for entry in inventory.entries],
        )
        self.assertEqual(EMULATOR_SERIAL, diagnostics.choose_serial(inventory, ""))
        self.assertEqual(PHONE_SERIAL, diagnostics.choose_serial(inventory, PHONE_SERIAL))
        self.assert_sanitized(inventory.sanitized)
        self.assertIn("<emulator-1>", inventory.sanitized)
        self.assertIn("<emulator-2>", inventory.sanitized)
        self.assertIn("<device-1>", inventory.sanitized)
        self.assertIn("<device-2>", inventory.sanitized)
        self.assertIn("offline", inventory.sanitized)
        self.assertIn("unauthorized", inventory.sanitized)

    def test_sanitization_is_idempotent(self) -> None:
        inventory = diagnostics.parse_inventory(DEVICES_OUTPUT)
        redactor = diagnostics.Redactor(inventory.identifiers)
        first = diagnostics.sanitize_text(inventory.sanitized, redactor)
        second = diagnostics.sanitize_text(first, redactor)

        self.assertEqual(first, second)
        self.assertIn("<emulator-1>", second)
        self.assert_sanitized(second)

    def test_runtime_log_uses_the_narrow_filter_and_silences_other_tags(self) -> None:
        result = self.collect("--runtime-log", "--serial", EMULATOR_SERIAL)

        self.assertEqual(0, result.returncode, result.stderr)
        text = self.read_log("android-runtime.log")
        self.assertIn("filter AndroidRuntime:E *:S", text)
        self.assertIn("AndroidRuntime: crash", text)
        self.assertNotIn("OtherTag", text)
        self.assert_sanitized(text)

        logcat_argv = next(argv for argv in self.adb_argv() if "logcat" in argv)
        self.assertEqual(
            [
                "-s",
                EMULATOR_SERIAL,
                "logcat",
                "-d",
                "-v",
                "threadtime",
                "-t",
                "1000",
                "AndroidRuntime:E",
                "*:S",
            ],
            logcat_argv,
        )

    def test_explicit_serial_uses_that_device_and_redacts_every_visible_device(self) -> None:
        result = self.collect("--runtime-log", "--serial", PHONE_SERIAL)

        self.assertEqual(0, result.returncode, result.stderr)
        logcat_argv = next(argv for argv in self.adb_argv() if "logcat" in argv)
        self.assertEqual(PHONE_SERIAL, logcat_argv[logcat_argv.index("-s") + 1])
        self.assert_sanitized(self.read_log("android-runtime.log"))

    def test_emulator_log_redacts_the_whole_multi_device_inventory(self) -> None:
        result = self.collect("--append-emulator", "--heading", "Inventory test")

        self.assertEqual(0, result.returncode, result.stderr)
        text = self.read_log("emulator.log")
        self.assertIn("Inventory test", text)
        self.assertIn("<emulator-1>", text)
        self.assertIn("<emulator-2>", text)
        self.assertIn("<device-1>", text)
        self.assertIn("<device-2>", text)
        self.assertIn("transport_id:<redacted>", text)
        self.assert_sanitized(text)

    def test_existing_diagnostic_files_are_sanitized_but_keep_the_result(self) -> None:
        payload = "\n".join(
            [
                f"serial={EMULATOR_SERIAL} other={PHONE_SERIAL}",
                f"model={PHONE_MODEL} device={PHONE_DEVICE} product={PRODUCT}",
                f"mac={MAC} token={TOKEN}",
                f"Bearer {TOKEN}",
                "ro.serialno=" + OTHER_PHONE_SERIAL,
                "android_id=FAKE-ANDROID-ID",
                "transport_id:4",
                "e2e passed",
            ]
        )
        for name in ("e2e.log", "server.log", "instrumentation.log"):
            (self.log_dir / name).write_text(payload + "\n", encoding="utf-8")

        result = self.collect("--sensitive", TOKEN)

        self.assertEqual(0, result.returncode, result.stderr)
        combined = ""
        for name in ("e2e.log", "server.log", "instrumentation.log"):
            text = self.read_log(name)
            combined += text
            self.assertIn("e2e passed", text)
            self.assert_sanitized(text)
        self.assertNotIn("FAKE-ANDROID-ID", combined)
        self.assertIn("<mac-address>", combined)
        self.assertIn("<redacted>", combined)

    def test_large_existing_log_is_capped(self) -> None:
        (self.log_dir / "server.log").write_text("x" * 20000 + "\n", encoding="utf-8")

        result = self.collect("--file-limit", "2048")

        self.assertEqual(0, result.returncode, result.stderr)
        text = self.read_log("server.log")
        self.assertLessEqual(len(text.encode("utf-8")), 2048)
        self.assertIn("truncated", text)

    def test_no_androidruntime_errors_is_explicit(self) -> None:
        env = self.environment(
            FAKE_ADB_LOGCAT="--------- beginning of main\n",
            FAKE_ADB_FILTER_AWARE="1",
        )

        result = self.collect("--runtime-log", env=env)

        self.assertEqual(0, result.returncode, result.stderr)
        text = self.read_log("android-runtime.log")
        self.assertIn("[no AndroidRuntime errors matched the current logcat buffer]", text)
        self.assertIn("filter AndroidRuntime:E *:S", text)

    def test_only_file_sanitizes_only_the_named_files(self) -> None:
        (self.log_dir / "server.log").write_text(f"serial={EMULATOR_SERIAL}\n", encoding="utf-8")
        (self.log_dir / "e2e.log").write_text(f"serial={EMULATOR_SERIAL}\n", encoding="utf-8")

        result = self.collect("--only-file", "server.log")

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertNotIn(EMULATOR_SERIAL, self.read_log("server.log"))
        self.assertIn(EMULATOR_SERIAL, self.read_log("e2e.log"))

    def test_adb_devices_failure_is_reported_without_raw_identifiers(self) -> None:
        env = self.environment(
            FAKE_ADB_DEVICES=(
                f"error: device '{PHONE_SERIAL}' not found\n"
                f"{OTHER_EMULATOR_SERIAL} offline\n"
            ),
            FAKE_ADB_DEVICES_STATUS="1",
            FAKE_ADB_LOGCAT=(
                f"11-02 12:00:00.000 1 2 E AndroidRuntime: crash on {OTHER_EMULATOR_SERIAL}\n"
            ),
        )

        result = self.collect("--append-emulator", env=env)

        self.assertEqual(0, result.returncode, result.stderr)
        text = self.read_log("emulator.log")
        self.assertIn("[adb devices exited with status 1]", text)
        self.assertIn("<emulator-1>", text)
        self.assertIn("device '<redacted>' not found", text)
        self.assertNotIn(PHONE_SERIAL, text)
        self.assertNotIn(OTHER_EMULATOR_SERIAL, text)

    def test_logcat_line_limit_is_enforced(self) -> None:
        env = self.environment(
            FAKE_ADB_LOGCAT="",
            FAKE_ADB_LOGCAT_LINES="500",
            FAKE_ADB_FILTER_AWARE="0",
        )

        result = self.collect("--runtime-log", "--line-limit", "25", "--byte-limit", "1000000", env=env)

        self.assertEqual(0, result.returncode, result.stderr)
        text = self.read_log("android-runtime.log")
        self.assertEqual(25, text.count("fake failure"))
        self.assertIn("earlier line(s) omitted", text)
        self.assertLess(len(text.encode("utf-8")), 1000000)

    def test_logcat_byte_limit_is_enforced(self) -> None:
        env = self.environment(
            FAKE_ADB_LOGCAT="",
            FAKE_ADB_LOGCAT_LINES="5000",
            FAKE_ADB_FILTER_AWARE="0",
        )

        result = self.collect("--runtime-log", "--line-limit", "1000", "--byte-limit", "2048", env=env)

        self.assertEqual(0, result.returncode, result.stderr)
        text = self.read_log("android-runtime.log")
        self.assertIn("[logcat output truncated at 2048 bytes]", text)
        self.assertLessEqual(len(text.encode("utf-8")), 8192)

    def test_run_bounded_times_out(self) -> None:
        started = time.monotonic()
        result = diagnostics.run_bounded(
            [sys.executable, "-c", "import time; time.sleep(5)"],
            timeout=0.2,
            max_bytes=1024,
        )
        elapsed = time.monotonic() - started

        self.assertTrue(result.timed_out)
        self.assertLess(elapsed, 3.0)

    def test_run_bounded_caps_bytes(self) -> None:
        result = diagnostics.run_bounded(
            [sys.executable, "-c", "import sys; sys.stdout.write('x' * 100000)"],
            timeout=5,
            max_bytes=1000,
        )

        self.assertTrue(result.truncated)
        self.assertLessEqual(len(result.output.encode("utf-8")), 1000)

    def _make_e2e_repo(self) -> tuple:
        repo = self.temp_dir / "repo"
        scripts = repo / "scripts"
        scripts.mkdir(parents=True)
        shutil.copy2(REPO_ROOT / "scripts" / "e2e-ci.sh", scripts / "e2e-ci.sh")
        shutil.copy2(REPO_ROOT / "scripts" / "e2e-diagnostics.py", scripts / "e2e-diagnostics.py")
        fake_e2e = scripts / "e2e.sh"
        fake_e2e.write_text(FAKE_E2E, encoding="utf-8")
        fake_e2e.chmod(0o755)
        self._write_fake_adb(repo / "sdk")
        logs = repo / "logs"
        logs.mkdir()
        return repo, logs

    def _run_e2e_ci(self, status: int, force: bool = False) -> tuple:
        repo, logs = self._make_e2e_repo()
        env = self.environment(
            ANDROID_HOME=str(repo / "sdk"),
            E2E_LOG_DIR=str(logs),
            FAKE_E2E_STATUS=str(status),
        )
        if force:
            env["E2E_FORCE_FAILURE"] = "true"
        result = subprocess.run(
            ["bash", str(repo / "scripts" / "e2e-ci.sh")],
            cwd=str(repo),
            env=env,
            capture_output=True,
            text=True,
            timeout=60,
        )
        return result, logs

    def _assert_uploaded_logs_are_sanitized(self, logs: Path, status: int) -> None:
        emulator = (logs / "emulator.log").read_text(encoding="utf-8")
        self.assertIn(f"E2E runner exit status: {status}", emulator)
        self.assertIn("<emulator-1>", emulator)
        self.assert_sanitized(emulator)
        for path in sorted(logs.glob("*.log")):
            self.assert_sanitized(path.read_text(encoding="utf-8"))

    def test_e2e_ci_failure_path_preserves_the_status_and_sanitizes_logs(self) -> None:
        result, logs = self._run_e2e_ci(status=1)

        self.assertEqual(1, result.returncode, result.stderr)
        self._assert_uploaded_logs_are_sanitized(logs, status=1)
        self.assertIn("e2e stdout", (logs / "e2e.log").read_text(encoding="utf-8"))
        self.assertIn("<redacted>", (logs / "e2e.log").read_text(encoding="utf-8"))

    def test_e2e_ci_success_path_preserves_the_status_and_sanitizes_logs(self) -> None:
        result, logs = self._run_e2e_ci(status=0)

        self.assertEqual(0, result.returncode, result.stderr)
        self._assert_uploaded_logs_are_sanitized(logs, status=0)
        self.assertIn("e2e stdout", (logs / "e2e.log").read_text(encoding="utf-8"))

    def test_e2e_ci_forced_failure_uploads_sanitized_useful_logs(self) -> None:
        result, logs = self._run_e2e_ci(status=0, force=True)

        self.assertEqual(1, result.returncode, result.stderr)
        self.assertIn("Intentional failure after E2E passed", result.stderr)
        self._assert_uploaded_logs_are_sanitized(logs, status=1)
        e2e_log = (logs / "e2e.log").read_text(encoding="utf-8")
        self.assertIn("e2e stdout", e2e_log)
        self.assertIn("<redacted>", e2e_log)

    def test_shell_scripts_parse_and_keep_the_real_assertions(self) -> None:
        for script in ("e2e.sh", "e2e-ci.sh"):
            completed = subprocess.run(
                ["bash", "-n", str(REPO_ROOT / "scripts" / script)],
                capture_output=True,
                text=True,
            )
            self.assertEqual(0, completed.returncode, completed.stderr)

        e2e = (REPO_ROOT / "scripts" / "e2e.sh").read_text(encoding="utf-8")
        self.assertIn('grep -q "OK (1 test)"', e2e)
        self.assertIn("assert len(brews) == 1", e2e)
        self.assertIn("--runtime-log", e2e)

        e2e_ci = (REPO_ROOT / "scripts" / "e2e-ci.sh").read_text(encoding="utf-8")
        self.assertIn("--append-emulator", e2e_ci)


if __name__ == "__main__":
    unittest.main()
