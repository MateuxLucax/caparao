"""Focused tests for scripts/e2e-diagnostics.py and the E2E diagnostic wrappers.

Every device identifier here is fake. A stub adb records its argv and emits a multi-device inventory plus
logcat lines containing fake serials, model names, a locally administered MAC address and a fake token.
The tests verify the AndroidRuntime filter, line/byte bounds, sanitization of every visible device and both
the success and failure paths of scripts/e2e-ci.sh without a real emulator or phone.
"""
from __future__ import annotations

import argparse
import contextlib
import errno
import importlib.util
import io
import json
import os
import shlex
import shutil
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path
from unittest import mock

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
printf '%s' "$E2E_AUTH_TOKEN" > "$E2E_RAW_LOG_DIR/expected-token"
printf 'e2e stdout serial=%s model=%s device=%s mac=%s token=%s\\n' \\
  "emulator-5554" "FAKE_MODEL_X" "fake_device_x" "02:00:00:00:00:01" "$E2E_AUTH_TOKEN"
printf 'server serial=%s token=%s\\n' "FAKEserial0001" "$E2E_AUTH_TOKEN" > "$E2E_RAW_LOG_DIR/server.log"
printf 'instrumentation emulator=%s\\n' "emulator-5556" > "$E2E_RAW_LOG_DIR/instrumentation.log"
exit "${FAKE_E2E_STATUS:-0}"
"""


class E2EDiagnosticsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = Path(tempfile.mkdtemp(prefix="caparao-e2e-diagnostics-"))
        self.addCleanup(shutil.rmtree, self.temp_dir, ignore_errors=True)
        self.log_dir = self.temp_dir / "logs"
        self.log_dir.mkdir()
        self.raw_dir = self.temp_dir / "raw"
        self.raw_dir.mkdir()
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

    def collect(
        self,
        *extra: str,
        env: dict = None,
        raw_dir: Path = None,
        log_dir: Path = None,
    ) -> subprocess.CompletedProcess:
        command = [
            sys.executable,
            str(SCRIPT),
            "collect",
            "--log-dir",
            str(log_dir or self.log_dir),
            "--adb",
            str(self.adb),
        ]
        if raw_dir is not None:
            command.extend(["--raw-dir", str(raw_dir)])
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

    def test_numeric_identifiers_are_redacted_as_complete_values(self) -> None:
        inventory = diagnostics.parse_inventory("5554 device\n")
        redactor = diagnostics.Redactor(inventory.identifiers)
        redactor.add("1234567890123456")

        result = redactor.redact(
            "connected to 5554; run token 1234567890123456; "
            "nearby values 15554, 55540, 12345678901234560 and dose 18.0"
        )

        self.assertIn("connected to <redacted>", result)
        self.assertIn("run token <redacted>", result)
        self.assertIn("15554, 55540, 12345678901234560", result)
        self.assertIn("dose 18.0", result)

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
            (self.raw_dir / name).write_text(payload + "\n", encoding="utf-8")

        result = self.collect(
            raw_dir=self.raw_dir,
            env=self.environment(E2E_DIAGNOSTICS_SENSITIVE=TOKEN),
        )

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
        (self.raw_dir / "server.log").write_text("x" * 20000 + "\n", encoding="utf-8")

        result = self.collect("--file-limit", "2048", raw_dir=self.raw_dir)

        self.assertEqual(0, result.returncode, result.stderr)
        text = self.read_log("server.log")
        self.assertLessEqual(len(text.encode("utf-8")), 2048)
        self.assertIn("truncated", text)

    def test_raw_log_is_not_published_when_sanitized_output_write_fails(self) -> None:
        source = self.raw_dir / "server.log"
        source.write_text(f"connected to 5554 token {TOKEN}\n", encoding="utf-8")
        output = self.temp_dir / "safe-artifacts"
        args = argparse.Namespace(
            log_dir=str(output),
            raw_dir=str(self.raw_dir),
            existing_safe_logs=False,
            only_file=["server.log"],
            adb=str(self.adb),
            serial="",
            status=1,
            heading="failure",
            append_emulator=False,
            runtime_log=False,
            line_limit=1000,
            byte_limit=512 * 1024,
            timeout=30.0,
            file_limit=1024 * 1024,
        )
        original_open = Path.open

        def fail_staged_write(path: Path, mode: str = "r", *positional, **keywords):
            if (
                path.name == "server.log"
                and path.parent.name.startswith(f".{output.name}.")
                and "w" in mode
            ):
                raise OSError(errno.EIO, "injected write failure")
            return original_open(path, mode, *positional, **keywords)

        stderr = io.StringIO()
        with mock.patch.dict(
            os.environ,
            self.environment(E2E_DIAGNOSTICS_SENSITIVE=TOKEN),
        ):
            with mock.patch.object(Path, "open", fail_staged_write):
                with contextlib.redirect_stderr(stderr):
                    result = diagnostics.collect(args)

        self.assertEqual(1, result)
        self.assertIn("could not sanitize server.log", stderr.getvalue())
        self.assertIn("Input/output error", stderr.getvalue())
        self.assertNotIn(str(self.temp_dir), stderr.getvalue())
        self.assertFalse(output.exists())
        self.assertEqual(f"connected to 5554 token {TOKEN}\n", source.read_text(encoding="utf-8"))

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
        (self.raw_dir / "server.log").write_text(f"serial={EMULATOR_SERIAL}\n", encoding="utf-8")
        (self.raw_dir / "e2e.log").write_text(f"serial={EMULATOR_SERIAL}\n", encoding="utf-8")

        result = self.collect("--only-file", "server.log", raw_dir=self.raw_dir)

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertNotIn(EMULATOR_SERIAL, self.read_log("server.log"))
        self.assertFalse((self.log_dir / "e2e.log").exists())
        self.assertIn(EMULATOR_SERIAL, (self.raw_dir / "e2e.log").read_text(encoding="utf-8"))

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
        raw_logs = repo / "raw-logs"
        raw_logs.mkdir()
        (raw_logs / "emulator.log").write_text(
            f"emulator preflight serial={OTHER_EMULATOR_SERIAL} model={EMULATOR_MODEL}\n",
            encoding="utf-8",
        )

        bin_dir = repo / "bin"
        bin_dir.mkdir()
        python_wrapper = bin_dir / "python3"
        python_wrapper.write_text(
            "#!/bin/sh\n"
            'if [ "${1:-}" = "scripts/e2e-diagnostics.py" ]; then\n'
            '  printf \'%s\\n\' "$*" >> "$E2E_HELPER_ARGS_LOG"\n'
            "fi\n"
            f"exec {shlex.quote(sys.executable)} \"$@\"\n",
            encoding="utf-8",
        )
        python_wrapper.chmod(0o755)
        openssl = bin_dir / "openssl"
        openssl.write_text("#!/bin/sh\nprintf '1234567890123456\\n'\n", encoding="utf-8")
        openssl.chmod(0o755)
        fault_dir = repo / "fault-injection"
        fault_dir.mkdir()
        (fault_dir / "sitecustomize.py").write_text(
            "import os\n"
            "from pathlib import Path\n"
            "_original_open = Path.open\n"
            "def _open(path, mode='r', *args, **kwargs):\n"
            "    if (os.environ.get('INJECT_DIAGNOSTIC_WRITE_FAILURE') == '1'\n"
            "            and path.parent.name.startswith('.logs.') and 'w' in mode):\n"
            "        raise OSError(5, 'injected artifact write failure')\n"
            "    return _original_open(path, mode, *args, **kwargs)\n"
            "Path.open = _open\n",
            encoding="utf-8",
        )
        helper_args = repo / "helper-args.txt"
        return repo, logs, raw_logs, bin_dir, helper_args, fault_dir

    def _run_e2e_ci(
        self,
        status: int,
        force: bool = False,
        fail_sanitization: bool = False,
    ) -> tuple:
        repo, logs, raw_logs, bin_dir, helper_args, fault_dir = self._make_e2e_repo()
        env = self.environment(
            ANDROID_HOME=str(repo / "sdk"),
            E2E_LOG_DIR=str(logs),
            E2E_RAW_LOG_DIR=str(raw_logs),
            E2E_HELPER_ARGS_LOG=str(helper_args),
            FAKE_E2E_STATUS=str(status),
            PATH=f"{bin_dir}{os.pathsep}{os.environ.get('PATH', '')}",
        )
        if fail_sanitization:
            env["INJECT_DIAGNOSTIC_WRITE_FAILURE"] = "1"
            env["PYTHONPATH"] = str(fault_dir)
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
        return result, logs, raw_logs, helper_args

    def _assert_uploaded_logs_are_sanitized(self, logs: Path, status: int, token: str) -> None:
        emulator = (logs / "emulator.log").read_text(encoding="utf-8")
        self.assertIn(f"E2E runner exit status: {status}", emulator)
        self.assertIn("<emulator-1>", emulator)
        self.assert_sanitized(emulator)
        for path in sorted(logs.glob("*.log")):
            text = path.read_text(encoding="utf-8")
            self.assert_sanitized(text)
            self.assertNotIn(token, text)
        self.assertFalse((logs / "expected-token").exists())

    def _assert_helper_argv_does_not_contain_token(self, helper_args: Path, token: str) -> None:
        argv = helper_args.read_text(encoding="utf-8")
        self.assertNotIn(token, argv)
        self.assertNotIn("--sensitive", argv)

    def test_e2e_ci_failure_path_preserves_the_status_and_sanitizes_logs(self) -> None:
        result, logs, raw_logs, helper_args = self._run_e2e_ci(status=1)

        self.assertEqual(1, result.returncode, result.stderr)
        token = (raw_logs / "expected-token").read_text(encoding="utf-8")
        self._assert_uploaded_logs_are_sanitized(logs, status=1, token=token)
        self._assert_helper_argv_does_not_contain_token(helper_args, token)
        self.assertIn("e2e stdout", (logs / "e2e.log").read_text(encoding="utf-8"))
        self.assertIn("<redacted>", (logs / "e2e.log").read_text(encoding="utf-8"))
        runtime = (logs / "android-runtime.log").read_text(encoding="utf-8")
        self.assertIn("AndroidRuntime: crash", runtime)
        self.assertIn("filter AndroidRuntime:E *:S", runtime)

    def test_e2e_ci_success_path_preserves_the_status_and_sanitizes_logs(self) -> None:
        result, logs, raw_logs, helper_args = self._run_e2e_ci(status=0)

        self.assertEqual(0, result.returncode, result.stderr)
        token = (raw_logs / "expected-token").read_text(encoding="utf-8")
        self._assert_uploaded_logs_are_sanitized(logs, status=0, token=token)
        self._assert_helper_argv_does_not_contain_token(helper_args, token)
        self.assertIn("e2e stdout", (logs / "e2e.log").read_text(encoding="utf-8"))
        self.assertFalse((logs / "android-runtime.log").exists())

    def test_e2e_ci_forced_failure_uploads_sanitized_useful_logs(self) -> None:
        result, logs, raw_logs, helper_args = self._run_e2e_ci(status=0, force=True)

        self.assertEqual(1, result.returncode, result.stderr)
        self.assertIn("Intentional failure after E2E passed", result.stderr)
        token = (raw_logs / "expected-token").read_text(encoding="utf-8")
        self._assert_uploaded_logs_are_sanitized(logs, status=1, token=token)
        self._assert_helper_argv_does_not_contain_token(helper_args, token)
        e2e_log = (logs / "e2e.log").read_text(encoding="utf-8")
        self.assertIn("e2e stdout", e2e_log)
        self.assertIn("<redacted>", e2e_log)

    def test_e2e_ci_sanitization_write_failure_keeps_raw_logs_out_of_artifact_path(self) -> None:
        result, logs, raw_logs, _ = self._run_e2e_ci(status=1, fail_sanitization=True)

        self.assertEqual(1, result.returncode, result.stderr)
        self.assertIn("could not sanitize", result.stderr)
        self.assertIn("no raw logs were published", result.stderr)
        self.assertFalse(logs.exists())
        self.assertIn(EMULATOR_SERIAL, (raw_logs / "e2e.log").read_text(encoding="utf-8"))

    def test_e2e_script_failure_captures_runtime_log_and_preserves_exit_status(self) -> None:
        repo = self.temp_dir / "actual-e2e"
        scripts = repo / "scripts"
        scripts.mkdir(parents=True)
        shutil.copy2(REPO_ROOT / "scripts" / "e2e.sh", scripts / "e2e.sh")
        shutil.copy2(REPO_ROOT / "scripts" / "e2e-diagnostics.py", scripts / "e2e-diagnostics.py")
        (repo / "stub").mkdir()
        logs = repo / "safe-logs"
        gradlew = repo / "gradlew"
        gradlew.write_text("#!/bin/sh\necho 'intentional fake build failure' >&2\nexit 23\n", encoding="utf-8")
        gradlew.chmod(0o755)

        bin_dir = repo / "bin"
        bin_dir.mkdir()
        fake_go = bin_dir / "go"
        fake_go.write_text(
            "#!/bin/sh\n"
            "output=''\n"
            "while [ $# -gt 0 ]; do\n"
            "  if [ \"$1\" = '-o' ]; then shift; output=$1; fi\n"
            "  shift\n"
            "done\n"
            "cat > \"$output\" <<'SERVER'\n"
            "#!/bin/sh\nexec sleep 60\n"
            "SERVER\n"
            "chmod +x \"$output\"\n",
            encoding="utf-8",
        )
        fake_go.chmod(0o755)
        fake_curl = bin_dir / "curl"
        fake_curl.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
        fake_curl.chmod(0o755)
        self._write_fake_adb(repo / "sdk")

        env = self.environment(
            ANDROID_HOME=str(repo / "sdk"),
            ANDROID_SERIAL=EMULATOR_SERIAL,
            E2E_LOG_DIR=str(logs),
            E2E_AUTH_TOKEN="1234567890123456",
            FAKE_ADB_LOGCAT=LOGCAT_OUTPUT,
            PATH=f"{bin_dir}{os.pathsep}{os.environ.get('PATH', '')}",
        )
        result = subprocess.run(
            ["bash", str(scripts / "e2e.sh")],
            cwd=str(repo),
            env=env,
            capture_output=True,
            text=True,
            timeout=30,
        )

        self.assertEqual(23, result.returncode, result.stderr)
        runtime = (logs / "android-runtime.log").read_text(encoding="utf-8")
        self.assertIn("E2E diagnostics for status 23", runtime)
        self.assertIn("AndroidRuntime: crash", runtime)
        self.assertIn("filter AndroidRuntime:E *:S", runtime)
        self.assert_sanitized(runtime)
        self.assertNotIn("1234567890123456", runtime)
        self.assertIn("intentional fake build failure", result.stderr)

    def test_e2e_ci_sanitization_failure_does_not_turn_a_pass_into_failure(self) -> None:
        result, logs, _, _ = self._run_e2e_ci(status=0, fail_sanitization=True)

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("no raw logs were published", result.stderr)
        self.assertFalse(logs.exists())

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
