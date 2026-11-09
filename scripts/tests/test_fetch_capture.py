import gzip
import importlib.util
import json
from pathlib import Path
import unittest
from urllib.request import Request

path = Path(__file__).resolve().parents[1] / "capture" / "fetch-capture.py"
spec = importlib.util.spec_from_file_location("fetch_capture", path)
fetch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fetch)


class CaptureConversionTest(unittest.TestCase):
    def records(self):
        return [
            {"format": "caparao-diagnostic-jsonl", "format_version": 1},
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

    def test_rejects_bad_sequence_and_missing_footer(self):
        records = self.records()
        records[2]["seq"] = 0
        with self.assertRaises(ValueError):
            fetch.convert_capture(self.encode(records))
        with self.assertRaises(ValueError):
            fetch.convert_capture(self.encode(self.records()[:-1]))

    def test_redirect_does_not_send_token_to_another_host(self):
        request = Request("https://sentry.io/api/0/attachment", headers={"Authorization": "Bearer secret"})
        redirected = fetch.SafeRedirect().redirect_request(request, None, 302, "Found", {}, "https://storage.example.com/file")
        self.assertIsNone(redirected.get_header("Authorization"))
        with self.assertRaises(ValueError):
            fetch.SafeRedirect().redirect_request(request, None, 302, "Found", {}, "http://storage.example.com/file")

    def test_rejects_unknown_header_and_bad_hex(self):
        records = self.records()
        records[0]["format_version"] = 2
        with self.assertRaises(ValueError):
            fetch.convert_capture(self.encode(records))
        records = self.records()
        records[1]["hex"] = "zz"
        with self.assertRaises(ValueError):
            fetch.convert_capture(self.encode(records))


if __name__ == "__main__":
    unittest.main()
