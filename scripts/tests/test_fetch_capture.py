import gzip
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from datetime import datetime, timezone
from urllib.request import Request

path = Path(__file__).resolve().parents[1] / "capture" / "fetch-capture.py"
spec = importlib.util.spec_from_file_location("fetch_capture", path)
fetch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fetch)
capture_spec = importlib.util.spec_from_file_location(
    "capture_session", Path(__file__).resolve().parents[1] / "capture" / "capture-session.py",
)
capture = importlib.util.module_from_spec(capture_spec)
sys.modules[capture_spec.name] = capture
capture_spec.loader.exec_module(capture)


class CaptureConversionTest(unittest.TestCase):
    def records(self):
        return [
            {"format": "caparao-diagnostic-jsonl", "format_version": 2, "app_version": "test", "phone_model": "synthetic", "android_version": "test", "started_at": "2026-11-01T00:00:00Z"},
            {"seq": 0, "t_ms": 0, "dir": "rx", "hex": "A5 5A", "note": "notification"},
            {"seq": 1, "t_ms": 1, "dir": "rx", "hex": "01 01", "note": "notification"},
            {"seq": 2, "t_ms": 1, "dir": "rx", "hex": "01 01", "note": "notification"},
            {"seq": 3, "t_ms": 2, "dir": "event", "hex": "", "note": "session_end", "complete": True},
        ]

    def encode(self, records):
        return gzip.compress(("\n".join(map(json.dumps, records)) + "\n").encode())

    def test_preserves_notification_boundaries_and_duplicates(self):
        records = self.records()
        self.assertEqual([json.loads(line) for line in fetch.convert_capture(self.encode(records)).splitlines()], records)

    def test_committed_synthetic_capture_converts_for_replay(self):
        fixture = Path(__file__).parent / "fixtures" / "synthetic-capture.jsonl.gz"
        rows = [json.loads(line) for line in fetch.convert_capture(fixture.read_bytes()).splitlines()]
        self.assertEqual(["session_started", "notification", "command", "notification", "notification", "session_end"], [row["note"] for row in rows[1:]])
        self.assertEqual(rows[-2]["hex"], rows[-3]["hex"])

    def test_developer_capture_script_writes_the_fetchable_format(self):
        with tempfile.TemporaryDirectory() as temp:
            log = capture.SessionLog(
                "synthetic", Path(temp), started_at=datetime(2026, 11, 1, tzinfo=timezone.utc),
                start_ns=0, monotonic_ns=lambda: 1_000_000,
            )
            log.rx(bytes.fromhex("A5 5A"))
            log.close()
            rows = [json.loads(line) for line in fetch.convert_capture(gzip.compress(log.path.read_bytes())).splitlines()]
            self.assertEqual(2, rows[0]["format_version"])
            self.assertEqual(["notification", "session_end"], [row["note"] for row in rows[1:]])

    def test_rejects_bad_sequence_and_missing_footer(self):
        records = self.records()
        records[2]["seq"] = 0
        with self.assertRaises(ValueError):
            fetch.convert_capture(self.encode(records))
        with self.assertRaises(ValueError):
            fetch.convert_capture(self.encode(self.records()[:-1]))

    def test_redirect_strips_auth_and_cookies_and_limits_hops(self):
        request = Request("https://sentry.io/api/0/attachment", headers={"authorization": "Bearer secret", "cookie": "session=secret"})
        redirect = fetch.SafeRedirect()
        redirected = redirect.redirect_request(request, None, 302, "Found", {}, "https://storage.example.com/file")
        self.assertIsNone(redirected.get_header("Authorization"))
        self.assertIsNone(redirected.get_header("Cookie"))
        with self.assertRaises(ValueError):
            redirect.redirect_request(redirected, None, 302, "Found", {}, "http://storage.example.com/file")
        for _ in range(2):
            redirected = redirect.redirect_request(redirected, None, 302, "Found", {}, "https://storage.example.com/file")
        with self.assertRaises(ValueError):
            redirect.redirect_request(redirected, None, 302, "Found", {}, "https://storage.example.com/file")

    def test_rejects_unknown_header_and_bad_hex(self):
        records = self.records()
        records[0]["format_version"] = 1
        with self.assertRaises(ValueError):
            fetch.convert_capture(self.encode(records))
        records = self.records()
        records[0]["phone_model"] = ""
        with self.assertRaises(ValueError):
            fetch.convert_capture(self.encode(records))

    def test_validates_time_ranges(self):
        self.assertEqual(("2026-11-01T00:00:00Z", "2026-11-02T00:00:00Z"), fetch.parse_time_range("2026-11-01T00:00:00Z", "2026-11-02T00:00:00Z"))
        with self.assertRaises(ValueError):
            fetch.parse_time_range("2026-11-02T00:00:00Z", "2026-11-01T00:00:00Z")
        records = self.records()
        records[1]["hex"] = "zz"
        with self.assertRaises(ValueError):
            fetch.convert_capture(self.encode(records))


if __name__ == "__main__":
    unittest.main()
