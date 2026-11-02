"""Unit tests for scripts/capture-session.py. No Bluetooth adapter or bleak installation is required."""
from __future__ import annotations

import asyncio
import importlib.util
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = REPO_ROOT / "scripts" / "capture-session.py"
SPEC = importlib.util.spec_from_file_location("capture_session", SCRIPT)
capture = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = capture
SPEC.loader.exec_module(capture)

FIXED_NOW = datetime(2026, 11, 2, 12, 34, 56, tzinfo=timezone.utc)
COMBINED_RX = bytes.fromhex(
    "A5 5A 03 06 00 01 01 00 00 "
    "A5 5A 01 01 00 09 00 00 00 00 00 00 00 00 00 00 00"
)


class FakeAdvertisement:
    def __init__(self, local_name=None):
        self.local_name = local_name


class FakeDevice:
    def __init__(self, name="TIMEMORE_Dot", address="AA:BB:CC:DD:EE:FF"):
        self.name = name
        self.address = address


class FakeScanner:
    def __init__(self, callback, device=None, local_name=None):
        self.callback = callback
        self.device = device
        self.local_name = local_name
        self.entered = False
        self.exited = False

    async def __aenter__(self):
        self.entered = True
        if self.device is not None:
            self.callback(self.device, FakeAdvertisement(self.local_name))
        return self

    async def __aexit__(self, exc_type, exc, tb):
        self.exited = True
        return False


class FakeClient:
    def __init__(self, stop_event=None, on_battery=None, fail_on=None, fail_connect=False):
        self.connected = False
        self.fail_connect = fail_connect
        self.subscribed = False
        self.stopped_notify = False
        self.disconnected = False
        self.writes = []
        self.slept = []
        self.calls = []
        self.stop_event = stop_event
        self.on_battery = on_battery
        self.fail_on = fail_on
        self.disconnect_callback = None
        self.notification_callback = None

    async def connect(self):
        self.calls.append("connect")
        if self.fail_connect:
            raise TimeoutError("connection timed out")
        self.connected = True

    async def start_notify(self, uuid, callback):
        self.calls.append("start_notify")
        self.subscribed = True
        self.notification_callback = callback

    async def write_gatt_char(self, uuid, data, response):
        self.calls.append("write")
        if self.fail_on is not None and data == self.fail_on:
            raise RuntimeError("write failed")
        self.writes.append((uuid, bytes(data), response))
        if data == capture.READ_BATTERY:
            if self.on_battery is not None:
                self.on_battery(self)
            if self.stop_event is not None:
                self.stop_event.set()

    async def stop_notify(self, uuid):
        self.calls.append("stop_notify")
        self.stopped_notify = True

    async def disconnect(self):
        self.calls.append("disconnect")
        self.disconnected = True

    def notify(self, data):
        if self.notification_callback is not None:
            self.notification_callback(None, bytearray(data))

    def lose_link(self):
        self.connected = False
        if self.disconnect_callback is not None:
            self.disconnect_callback(self)


class Clock:
    def __init__(self, step_ms=10):
        self.value = 0
        self.step = step_ms * 1_000_000

    def monotonic_ns(self):
        current = self.value
        self.value += self.step
        return current


def read_records(path):
    lines = path.read_text(encoding="utf-8").splitlines()
    return json.loads(lines[0]), [json.loads(line) for line in lines[1:]]


class SessionLogTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.dir = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def log(self, label="espresso"):
        return capture.SessionLog(
            label,
            self.dir,
            wall_clock=lambda: FIXED_NOW,
            monotonic_ns=Clock().monotonic_ns,
        )

    def test_writes_header_rows_and_flushes_while_open(self):
        log = self.log()
        log.event("session_start")
        log.tx(capture.READ_BATTERY, "battery")
        log.rx(COMBINED_RX)

        header, records = read_records(log.path)

        self.assertEqual({"label", "started_at", "app"}, set(header))
        self.assertEqual("espresso", header["label"])
        self.assertEqual("2026-11-02T12:34:56Z", header["started_at"])
        self.assertEqual("capture-session.py", header["app"])
        self.assertEqual(["event", "tx", "rx"], [row["dir"] for row in records])
        self.assertEqual({"t_ms", "dir", "hex", "note"}, set(records[0]))
        self.assertEqual("", records[0]["hex"])
        self.assertEqual(capture.READ_BATTERY.hex(" ").upper(), records[1]["hex"])
        self.assertEqual(capture._hex(COMBINED_RX), records[2]["hex"])
        self.assertIn("A5 5A 01 01", records[2]["hex"])
        self.assertEqual(sorted(row["t_ms"] for row in records), [row["t_ms"] for row in records])
        self.assertTrue(all(isinstance(row["t_ms"], int) and row["t_ms"] >= 0 for row in records))

        log.close()
        self.assertFalse(log.active)

    def test_never_overwrites_an_existing_capture(self):
        first = self.log()
        first.close()
        with self.assertRaises(FileExistsError):
            self.log()

    def test_redacts_known_names_addresses_and_identifier_shapes(self):
        label = "TIMEMORE_TES017 AA:BB:CC:DD:EE:FF AABBCCDDEEFF 12345678-1234-1234-1234-123456789ABC"
        log = capture.SessionLog(
            label,
            self.dir,
            wall_clock=lambda: FIXED_NOW,
            monotonic_ns=Clock().monotonic_ns,
        )
        log.redactor.add("Kitchen Scale")
        log.event("connected to Kitchen Scale at AA:BB:CC:DD:EE:FF")
        log.close()

        contents = log.path.read_text(encoding="utf-8")
        for identifier in (
            "TIMEMORE_Dot",
            "TES017",
            "Kitchen Scale",
            "AA:BB:CC:DD:EE:FF",
            "AABBCCDDEEFF",
            "12345678-1234",
        ):
            self.assertNotIn(identifier, contents)
            self.assertNotIn(identifier, log.path.name)
        self.assertIn(capture.REDACTED, contents)

    def test_capture_directory_is_git_ignored(self):
        if shutil.which("git") is None:
            self.skipTest("git is not installed")
        result = subprocess.run(
            ["git", "check-ignore", "-q", "spike-captures/session.jsonl"],
            cwd=REPO_ROOT,
            check=False,
        )
        self.assertEqual(0, result.returncode)


class NameMatchingTest(unittest.TestCase):
    def test_accepts_only_the_two_dot_names(self):
        accepted = ["TIMEMORE_Dot", "TES017", "TIMEMORE_TES017"]
        rejected = ["dot", "TIMEMORE", "Other scale", None, ""]
        for name in accepted:
            with self.subTest(name=name):
                self.assertTrue(capture.is_dot_name(name))
        for name in rejected:
            with self.subTest(name=name):
                self.assertFalse(capture.is_dot_name(name))


class CaptureSessionTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.dir = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def factories(self, device, stop_event=None, on_battery=None, fail_on=None, local_name=None):
        scanners = []
        clients = []

        def scanner_factory(callback):
            scanner = FakeScanner(callback, device, local_name)
            scanners.append(scanner)
            return scanner

        def client_factory(_device, disconnected_callback):
            client = FakeClient(stop_event=stop_event, on_battery=on_battery, fail_on=fail_on)
            client.disconnect_callback = disconnected_callback
            clients.append(client)
            return client

        return scanner_factory, client_factory, scanners, clients

    async def test_records_handshake_notifications_and_normal_stop(self):
        stop = asyncio.Event()
        asleep = []
        statuses = []

        async def sleep(seconds):
            asleep.append(seconds)

        scanner_factory, client_factory, scanners, clients = self.factories(
            FakeDevice(),
            stop_event=stop,
            on_battery=lambda client: client.notify(COMBINED_RX),
        )
        result = await capture.capture_session(
            "espresso 18g",
            output_dir=self.dir,
            scanner_factory=scanner_factory,
            client_factory=client_factory,
            sleep=sleep,
            stop_event=stop,
            wall_clock=lambda: FIXED_NOW,
            monotonic_ns=Clock().monotonic_ns,
            on_status=statuses.append,
        )

        self.assertEqual(0, result.exit_code)
        self.assertEqual("scanning for TIMEMORE_Dot (10 s)...", statuses[0])
        self.assertTrue(statuses[1].startswith("recording to "))
        self.assertEqual("connected; recording until Ctrl+C", statuses[-1])
        self.assertTrue(scanners[0].entered)
        self.assertTrue(scanners[0].exited)
        client = clients[0]
        self.assertEqual([capture.UNIT_GRAMS, capture.STANDARD_MODE, capture.READ_BATTERY],
                         [write[1] for write in client.writes])
        self.assertTrue(all(write[0] == capture.WRITE_UUID and write[2] is False for write in client.writes))
        self.assertLess(client.calls.index("start_notify"), client.calls.index("write"))
        self.assertEqual([capture.NOTIFY_DELAY_SECONDS, capture.MODE_DELAY_SECONDS], asleep)
        self.assertTrue(client.stopped_notify)
        self.assertTrue(client.disconnected)

        header, records = read_records(result.path)
        self.assertEqual("espresso 18g", header["label"])
        events = [row["note"] for row in records if row["dir"] == "event"]
        self.assertEqual(
            [
                "session_start",
                "scan_start",
                "scan_match",
                "connect_attempt 1",
                "connected",
                "notifications_enabled",
                "handshake_done",
                "disconnect_requested",
                "disconnected",
                "session_end",
            ],
            events,
        )
        rx = [row for row in records if row["dir"] == "rx"]
        self.assertEqual(1, len(rx))
        self.assertEqual(capture._hex(COMBINED_RX), rx[0]["hex"])
        self.assertEqual(
            ["unit grams", "standard mode", "battery"],
            [row["note"] for row in records if row["dir"] == "tx"],
        )

    async def test_scan_accepts_the_advertised_local_name_when_device_name_is_missing(self):
        device = FakeDevice(name=None)

        def scanner_factory(callback):
            return FakeScanner(callback, device, local_name="TIMEMORE_TES017")

        found = await capture.scan_for_dot(scanner_factory, timeout=0.1)

        self.assertIsNotNone(found)
        self.assertIs(found.device, device)
        self.assertIn("TIMEMORE_TES017", found.identifiers)
        self.assertIn("AA:BB:CC:DD:EE:FF", found.identifiers)

    async def test_custom_device_name_in_label_is_redacted_from_header_and_filename(self):
        stop = asyncio.Event()
        scanner_factory, client_factory, _, _ = self.factories(
            FakeDevice(name="Kitchen Scale"),
            stop_event=stop,
            local_name="TIMEMORE_TES017",
        )
        result = await capture.capture_session(
            "Kitchen Scale session",
            output_dir=self.dir,
            scanner_factory=scanner_factory,
            client_factory=client_factory,
            sleep=self.no_sleep,
            stop_event=stop,
            wall_clock=lambda: FIXED_NOW,
            monotonic_ns=Clock().monotonic_ns,
        )

        self.assertEqual(0, result.exit_code)
        contents = result.path.read_text(encoding="utf-8")
        self.assertNotIn("Kitchen Scale", contents)
        self.assertNotIn("Kitchen Scale", result.path.name)

    async def test_scan_timeout_is_recorded_and_closes_the_file(self):
        scanner_factory, client_factory, _, clients = self.factories(None)
        result = await capture.capture_session(
            "no scale",
            output_dir=self.dir,
            scanner_factory=scanner_factory,
            client_factory=client_factory,
            scan_timeout=0.001,
            wall_clock=lambda: FIXED_NOW,
            monotonic_ns=Clock().monotonic_ns,
        )

        self.assertEqual(1, result.exit_code)
        self.assertEqual([], clients)
        _, records = read_records(result.path)
        self.assertEqual(["session_start", "scan_start", "scan_timeout", "session_end"],
                         [row["note"] for row in records])

    async def test_connection_is_retried_three_times(self):
        calls = []

        class FailingClient:
            def __init__(self, attempt):
                self.attempt = attempt

            async def connect(self):
                calls.append(self.attempt)
                raise TimeoutError("device address should never be logged")

            async def disconnect(self):
                pass

        def client_factory(_device, _callback):
            return FailingClient(len(calls) + 1)

        scanner_factory, _, _, _ = self.factories(FakeDevice())
        sleeps = []

        async def sleep(seconds):
            sleeps.append(seconds)

        result = await capture.capture_session(
            "retry",
            output_dir=self.dir,
            scanner_factory=scanner_factory,
            client_factory=client_factory,
            sleep=sleep,
            wall_clock=lambda: FIXED_NOW,
            monotonic_ns=Clock().monotonic_ns,
        )

        self.assertEqual(1, result.exit_code)
        self.assertEqual([1, 2, 3], calls)
        self.assertEqual([capture.CONNECT_RETRY_SECONDS] * 2, sleeps)
        _, records = read_records(result.path)
        self.assertEqual(
            [
                "session_start",
                "scan_start",
                "scan_match",
                "connect_attempt 1",
                "connect_failed TimeoutError",
                "connect_attempt 2",
                "connect_failed TimeoutError",
                "connect_attempt 3",
                "connect_failed TimeoutError",
                "connect_failed_all",
                "session_end",
            ],
            [row["note"] for row in records],
        )
        self.assertNotIn("AA:BB:CC", result.path.read_text(encoding="utf-8"))

    async def test_unexpected_disconnect_ends_the_capture_with_failure(self):
        def lose_link(client):
            client.notify(COMBINED_RX)
            client.lose_link()

        scanner_factory, client_factory, _, clients = self.factories(
            FakeDevice(),
            on_battery=lose_link,
        )
        result = await capture.capture_session(
            "dropped",
            output_dir=self.dir,
            scanner_factory=scanner_factory,
            client_factory=client_factory,
            sleep=self.no_sleep,
            wall_clock=lambda: FIXED_NOW,
            monotonic_ns=Clock().monotonic_ns,
        )

        self.assertEqual(1, result.exit_code)
        _, records = read_records(result.path)
        events = [row["note"] for row in records if row["dir"] == "event"]
        self.assertIn("disconnected", events)
        self.assertNotIn("disconnect_requested", events)
        self.assertEqual("session_end", events[-1])
        self.assertTrue(clients[0].disconnected)

    async def test_write_failure_still_subscribes_cleans_up_and_logs_safely(self):
        stop = asyncio.Event()
        scanner_factory, client_factory, _, clients = self.factories(
            FakeDevice(),
            stop_event=stop,
            fail_on=capture.STANDARD_MODE,
        )
        result = await capture.capture_session(
            "write failure",
            output_dir=self.dir,
            scanner_factory=scanner_factory,
            client_factory=client_factory,
            sleep=self.no_sleep,
            stop_event=stop,
            wall_clock=lambda: FIXED_NOW,
            monotonic_ns=Clock().monotonic_ns,
        )

        self.assertEqual(1, result.exit_code)
        client = clients[0]
        self.assertTrue(client.stopped_notify)
        self.assertTrue(client.disconnected)
        _, records = read_records(result.path)
        self.assertIn("capture_failed RuntimeError",
                      [row["note"] for row in records if row["dir"] == "event"])
        self.assertEqual("session_end", records[-1]["note"])

    async def test_cancellation_releases_the_connection(self):
        handshake_done = asyncio.Event()

        def mark_ready(client):
            client.notify(COMBINED_RX)
            handshake_done.set()

        scanner_factory, client_factory, _, clients = self.factories(
            FakeDevice(),
            on_battery=mark_ready,
        )
        task = asyncio.create_task(
            capture.capture_session(
                "cancelled",
                output_dir=self.dir,
                scanner_factory=scanner_factory,
                client_factory=client_factory,
                sleep=self.no_sleep,
                wall_clock=lambda: FIXED_NOW,
                monotonic_ns=Clock().monotonic_ns,
            )
        )
        await asyncio.wait_for(handshake_done.wait(), timeout=1)
        task.cancel()
        with self.assertRaises(asyncio.CancelledError):
            await task

        client = clients[0]
        self.assertTrue(client.stopped_notify)
        self.assertTrue(client.disconnected)
        self.assertEqual("session_end", self.last_event(self.dir))

    async def test_callback_from_a_failed_attempt_cannot_stop_the_current_capture(self):
        stop = asyncio.Event()
        clients = []

        def trigger_stale(active):
            clients[0].lose_link()
            active.notify(COMBINED_RX)

        def client_factory(_device, disconnected_callback):
            if not clients:
                client = FakeClient(fail_connect=True)
            else:
                client = FakeClient(stop_event=stop, on_battery=trigger_stale)
            client.disconnect_callback = disconnected_callback
            clients.append(client)
            return client

        scanner_factory, _, _, _ = self.factories(FakeDevice())
        result = await capture.capture_session(
            "stale callback",
            output_dir=self.dir,
            scanner_factory=scanner_factory,
            client_factory=client_factory,
            sleep=self.no_sleep,
            stop_event=stop,
            wall_clock=lambda: FIXED_NOW,
            monotonic_ns=Clock().monotonic_ns,
        )

        self.assertEqual(0, result.exit_code)
        events = [row["note"] for row in read_records(result.path)[1] if row["dir"] == "event"]
        self.assertEqual(1, events.count("disconnected"))
        self.assertEqual(["disconnect_requested", "disconnected", "session_end"], events[-3:])

    async def test_stop_already_requested_skips_waiting_for_disconnect(self):
        stop = asyncio.Event()
        stop.set()
        scanner_factory, client_factory, _, clients = self.factories(FakeDevice())
        result = await capture.capture_session(
            "already stopped",
            output_dir=self.dir,
            scanner_factory=scanner_factory,
            client_factory=client_factory,
            sleep=self.no_sleep,
            stop_event=stop,
            wall_clock=lambda: FIXED_NOW,
            monotonic_ns=Clock().monotonic_ns,
        )

        self.assertEqual(0, result.exit_code)
        events = [row["note"] for row in read_records(result.path)[1]]
        self.assertIn("disconnect_requested", events)
        self.assertTrue(clients[0].disconnected)

    async def no_sleep(self, _seconds):
        return None

    def last_event(self, directory):
        files = list(Path(directory).glob("*.jsonl"))
        self.assertEqual(1, len(files))
        return read_records(files[0])[1][-1]["note"]


class CliTest(unittest.TestCase):
    def test_help_does_not_require_bleak(self):
        result = subprocess.run(
            [sys.executable, str(SCRIPT), "--help"],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(0, result.returncode)
        self.assertIn("Record a Timemore Dot", result.stdout)
        self.assertIn("label", result.stdout)

    def test_label_is_required(self):
        result = subprocess.run(
            [sys.executable, str(SCRIPT)],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(2, result.returncode)
        self.assertIn("label", result.stderr)

    def test_parser_accepts_the_session_label(self):
        self.assertEqual("espresso 18g", capture._parser().parse_args(["espresso 18g"]).label)

    def test_module_import_does_not_import_bleak(self):
        self.assertNotIn("bleak", sys.modules)


if __name__ == "__main__":
    unittest.main()
