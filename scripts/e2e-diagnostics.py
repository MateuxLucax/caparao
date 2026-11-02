#!/usr/bin/env python3
"""Bounds and sanitizes end-to-end diagnostics before they become artifacts.

The repository and its CI artifacts are public. `adb devices -l` and logcat can contain device serials,
model/device names, transport ids, BLE addresses and run tokens, so every diagnostic file passes through
this helper before upload. Logcat capture names the exact tag filter (`AndroidRuntime:E` with `*:S`) so
unrelated tags cannot flood an artifact, and both line and byte limits bound what is kept.

Only the standard library and `python3` (already used by scripts/e2e.sh) are required.
"""
from __future__ import annotations

import argparse
import os
import re
import signal
import shutil
import stat
import subprocess
import sys
import tempfile
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional, Sequence, Tuple

REDACTED = "<redacted>"
DEFAULT_DEVICE_TIMEOUT = 10.0
DEFAULT_LOGCAT_TIMEOUT = 30.0
DEFAULT_LINE_LIMIT = 1000
DEFAULT_BYTE_LIMIT = 512 * 1024
DEFAULT_FILE_LIMIT = 1024 * 1024
DEVICES_BYTE_LIMIT = 64 * 1024

DEVICE_STATES = (
    "device",
    "offline",
    "unauthorized",
    "bootloader",
    "recovery",
    "sideload",
    "host",
    "connecting",
    "unknown",
)

# Device serials and product/model names are usually longer than this. Short numeric values such as
# transport ids are redacted in the inventory itself, but must not become global one-character replacements.
IGNORED_IDENTIFIERS = {"android", "device", "emulator", "generic", "host", "localhost", "unknown"}

_DASH_S = re.compile(r"(?<!\S)-s\s+\S+")
_QUOTED_DEVICE = re.compile(r"(?i)(\bdevice\s+['\"])([^'\"]{2,})(['\"])")
_EMULATOR_SERIAL = re.compile(r"(?<![\w<])emulator-\d+\b")
_MODEL_ATTRIBUTE = re.compile(r"\b(product|model|device|transport_id|usb):(\S+)")
_SERIALNO = re.compile(
    r"(?im)^(\s*(?:ro\.)?(?:serialno|boot\.serialno|serial(?:_number)?)\s*[=:]\s*)(\S+)"
)
_ANDROID_ID = re.compile(r"(?i)\b(android[_-]?id|device[_-]?id)(\s*[=:]\s*)[^\s,;]+")
_BEARER = re.compile(r"(?i)(\bBearer\s+)[A-Za-z0-9._~+/=-]+")
_TOKEN_KV = re.compile(
    r"(?i)\b(token|auth[_-]?token|password|secret)(\s*[=:]\s*)(['\"]?)([^\s'\",;]+)(\3)"
)
_MAC = re.compile(r"(?<![0-9A-Fa-f])(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}(?![0-9A-Fa-f])")

_DEVICE_LINE = re.compile(
    r"^(\S+)\s+(" + "|".join(DEVICE_STATES) + r")\b(.*)$"
)
_NO_PERMISSIONS_LINE = re.compile(r"^(\S+)\s+(no permissions\b.*)$")
_ATTRIBUTE = re.compile(r"([A-Za-z_][A-Za-z0-9_.-]*):(\S+)")


@dataclass
class CommandResult:
    output: str = ""
    status: Optional[int] = None
    timed_out: bool = False
    truncated: bool = False
    error: Optional[str] = None


@dataclass
class DeviceEntry:
    serial: str
    state: str
    attributes: List[Tuple[str, str]]


@dataclass
class Inventory:
    raw: str
    entries: List[DeviceEntry]
    identifiers: List[str]
    sanitized: str


class Redactor:
    """Removes known device identifiers and identifier-shaped text from diagnostics."""

    def __init__(self, sensitive: Sequence[str] = ()) -> None:
        self._sensitive: List[str] = []
        self.add(*sensitive)

    def add(self, *values: Optional[str]) -> None:
        for value in values:
            cleaned = (value or "").strip()
            if _useful_identifier(cleaned) and cleaned not in self._sensitive:
                self._sensitive.append(cleaned)
        self._sensitive.sort(key=len, reverse=True)

    def redact(self, text: str) -> str:
        result = str(text)
        for value in self._sensitive:
            escaped = re.escape(value)
            if value.isdigit():
                pattern = rf"(?<![A-Za-z0-9_.]){escaped}(?![A-Za-z0-9_.])"
            else:
                pattern = escaped
            result = re.sub(pattern, REDACTED, result, flags=re.IGNORECASE)
        result = _DASH_S.sub("-s " + REDACTED, result)
        result = _QUOTED_DEVICE.sub(r"\1" + REDACTED + r"\3", result)
        result = _EMULATOR_SERIAL.sub(REDACTED, result)
        result = _MODEL_ATTRIBUTE.sub(r"\1:" + REDACTED, result)
        result = _SERIALNO.sub(r"\1" + REDACTED, result)
        result = _ANDROID_ID.sub(r"\1\2" + REDACTED, result)
        result = _BEARER.sub(r"\1" + REDACTED, result)
        result = _TOKEN_KV.sub(r"\1\2\3" + REDACTED + r"\5", result)
        return _MAC.sub("<mac-address>", result)


def _useful_identifier(value: str) -> bool:
    if len(value) < 4:
        return False
    return value.lower() not in IGNORED_IDENTIFIERS


def _terminate(proc: subprocess.Popen[bytes]) -> None:
    if proc.poll() is not None:
        return
    try:
        os.killpg(os.getpgid(proc.pid), signal.SIGKILL)
    except (OSError, ProcessLookupError):
        try:
            proc.kill()
        except OSError:
            pass


def run_bounded(argv: Sequence[str], timeout: float, max_bytes: int) -> CommandResult:
    """Runs a command, keeping at most max_bytes and killing it after timeout seconds."""
    try:
        proc = subprocess.Popen(
            list(argv),
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            start_new_session=True,
        )
    except OSError as exc:
        return CommandResult(error=str(exc))

    chunks: List[bytes] = []
    total = 0
    truncated = False

    def reader() -> None:
        nonlocal total, truncated
        assert proc.stdout is not None
        fd = proc.stdout.fileno()
        while True:
            try:
                chunk = os.read(fd, 65536)
            except OSError:
                break
            if not chunk:
                break
            remaining = max(0, max_bytes - total)
            if remaining:
                chunks.append(chunk[:remaining])
                total += min(len(chunk), remaining)
            if len(chunk) > remaining:
                truncated = True
                _terminate(proc)
                break

    thread = threading.Thread(target=reader, name="e2e-diagnostics-reader", daemon=True)
    thread.start()
    status: Optional[int] = None
    timed_out = False
    try:
        status = proc.wait(timeout=timeout)
    except subprocess.TimeoutExpired:
        timed_out = True
        _terminate(proc)
        try:
            status = proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            status = None
    thread.join(timeout=5)
    if thread.is_alive():
        _terminate(proc)
        thread.join(timeout=1)
    if proc.stdout is not None:
        proc.stdout.close()
    return CommandResult(
        output=b"".join(chunks).decode("utf-8", "replace"),
        status=status,
        timed_out=timed_out,
        truncated=truncated,
    )


def parse_device_line(line: str) -> Optional[DeviceEntry]:
    match = _DEVICE_LINE.match(line)
    if match:
        serial, state, rest = match.group(1), match.group(2), match.group(3).strip()
        attributes = [(key, value) for key, value in _ATTRIBUTE.findall(rest)]
        free_text = _ATTRIBUTE.sub("", rest).strip()
        # adb error lines such as "error: device 'serial' not found" also have a known second token. A real
        # device line carries only attributes after the state, so leave anything else to the generic redactor.
        if free_text:
            return None
        return DeviceEntry(serial=serial, state=state, attributes=attributes)
    match = _NO_PERMISSIONS_LINE.match(line)
    if match:
        return DeviceEntry(serial=match.group(1), state=match.group(2).strip(), attributes=[])
    return None


def parse_inventory(raw: str) -> Inventory:
    entries: List[DeviceEntry] = []
    identifiers: List[str] = []
    for line in raw.splitlines():
        entry = parse_device_line(line)
        if entry is None:
            continue
        entries.append(entry)
        values = [entry.serial]
        values += [value for key, value in entry.attributes if key in ("product", "model", "device")]
        for value in values:
            if _useful_identifier(value) and value not in identifiers:
                identifiers.append(value)
    redactor = Redactor(identifiers)
    return Inventory(
        raw=raw,
        entries=entries,
        identifiers=identifiers,
        sanitized=sanitize_text(raw, redactor),
    )


def sanitize_text(text: str, redactor: Redactor) -> str:
    """Sanitizes any diagnostic text, keeping device lines structurally readable."""
    output: List[str] = []
    emulators = 0
    devices = 0
    for line in text.splitlines(keepends=True):
        body = line.rstrip("\r\n")
        ending = line[len(body):]
        if body.strip() == "List of devices attached":
            emulators = 0
            devices = 0
        entry = parse_device_line(body)
        if entry is None or entry.serial.startswith("<"):
            output.append(redactor.redact(line))
            continue
        if entry.serial.startswith("emulator-"):
            emulators += 1
            label = f"<emulator-{emulators}>"
        else:
            devices += 1
            label = f"<device-{devices}>"
        rendered = f"{label:<22} {redactor.redact(entry.state)}"
        if entry.attributes:
            rendered += " " + " ".join(f"{key}:{REDACTED}" for key, _ in entry.attributes)
        output.append(rendered + ending)
    return "".join(output)


def choose_serial(inventory: Inventory, explicit: Optional[str]) -> Optional[str]:
    if explicit and explicit.strip():
        return explicit.strip()
    for entry in inventory.entries:
        if entry.serial.startswith("emulator-") and entry.state == "device":
            return entry.serial
    for entry in inventory.entries:
        if entry.serial.startswith("emulator-"):
            return entry.serial
    for entry in inventory.entries:
        if entry.state == "device":
            return entry.serial
    for entry in inventory.entries:
        return entry.serial
    return None


def limit_lines(text: str, limit: int) -> Tuple[str, int]:
    lines = text.splitlines()
    if limit <= 0:
        return "", len(lines)
    if len(lines) <= limit:
        return text, 0
    dropped = len(lines) - limit
    return "\n".join(lines[-limit:]), dropped


def cap_text(text: str, limit: int) -> Tuple[str, bool]:
    """Caps UTF-8 text at limit bytes, keeping a head and tail with an explicit marker."""
    data = text.encode("utf-8")
    if len(data) <= limit:
        return text, False
    marker = f"\n[diagnostics truncated: kept the first and last bytes of {len(data)}]\n"
    marker_bytes = marker.encode("utf-8")
    if len(marker_bytes) >= limit:
        return marker_bytes[:limit].decode("utf-8", "ignore"), True
    keep = limit - len(marker_bytes)
    head_bytes = keep * 2 // 3
    tail_bytes = keep - head_bytes
    head = data[:head_bytes].decode("utf-8", "ignore")
    tail = data[-tail_bytes:].decode("utf-8", "ignore") if tail_bytes else ""
    return head + marker + tail, True


def _logcat_has_entries(text: str) -> bool:
    return any(
        line.strip() and not line.startswith("--------- beginning of ")
        for line in text.splitlines()
    )


def capture_logcat(
    adb: str,
    serial: Optional[str],
    redactor: Redactor,
    line_limit: int,
    byte_limit: int,
    timeout: float,
) -> str:
    header = (
        "--- AndroidRuntime errors "
        f"(filter AndroidRuntime:E *:S; limit {line_limit} lines / {byte_limit} bytes) ---"
    )
    if not serial:
        return (
            "\n".join(
                [
                    header,
                    "[no device serial was available for logcat]",
                    "--- end AndroidRuntime errors ---",
                ]
            )
            + "\n"
        )

    result = run_bounded(
        [
            adb,
            "-s",
            serial,
            "logcat",
            "-d",
            "-v",
            "threadtime",
            "-t",
            str(line_limit),
            "AndroidRuntime:E",
            "*:S",
        ],
        timeout,
        byte_limit,
    )
    body, dropped = limit_lines(redactor.redact(result.output), line_limit)
    body, byte_capped = cap_text(body, byte_limit)
    notices: List[str] = []
    if result.error:
        notices.append(f"[adb logcat could not start: {result.error}]")
    if result.timed_out:
        notices.append(f"[logcat timed out after {timeout:g}s; output may be incomplete]")
    if result.truncated or byte_capped:
        notices.append(f"[logcat output truncated at {byte_limit} bytes]")
    if result.status is not None and result.status != 0:
        notices.append(f"[adb logcat exited with status {result.status}]")
    if dropped:
        notices.append(f"[diagnostics truncated: {dropped} earlier line(s) omitted]")

    body = body.rstrip("\n")
    if (
        not _logcat_has_entries(body)
        and not result.error
        and not result.timed_out
        and result.status in (0, None)
    ):
        body = "[no AndroidRuntime errors matched the current logcat buffer]"

    parts = [header]
    parts.extend(notices)
    if body:
        parts.append(body)
    parts.append("--- end AndroidRuntime errors ---")
    return "\n".join(parts) + "\n"


def read_bounded(path: Path, limit: int) -> Tuple[bytes, bool]:
    size = path.stat().st_size
    if size <= limit:
        return path.read_bytes(), False
    head_bytes = max(0, limit * 2 // 3)
    tail_bytes = max(0, limit - head_bytes)
    with path.open("rb") as handle:
        head = handle.read(head_bytes)
        if tail_bytes:
            handle.seek(-tail_bytes, os.SEEK_END)
            tail = handle.read(tail_bytes)
        else:
            tail = b""
    omitted = max(0, size - len(head) - len(tail))
    marker = f"\n[diagnostics truncated: {omitted} bytes omitted from the middle]\n".encode("utf-8")
    return head + marker + tail, True


def sanitize_file(source: Path, destination: Path, redactor: Redactor, limit: int) -> None:
    raw, _ = read_bounded(source, limit)
    text = raw.decode("utf-8", "replace")
    clean = sanitize_text(text, redactor)
    clean, _ = cap_text(clean, limit)
    with destination.open("w", encoding="utf-8") as handle:
        handle.write(clean)


def _safe_io_error(exc: OSError) -> str:
    if exc.errno is None:
        return "I/O error"
    return f"{os.strerror(exc.errno)} (errno {exc.errno})"


def _write_emulator_log(
    log_dir: Path,
    redactor: Redactor,
    args: argparse.Namespace,
    device_result: CommandResult,
    inventory: Inventory,
    logcat_text: Optional[str],
) -> None:
    with (log_dir / "emulator.log").open("a", encoding="utf-8") as handle:
        handle.write(f"\n{redactor.redact(args.heading)}\n")
        if args.status is not None:
            handle.write(f"E2E runner exit status: {args.status}\n")
        handle.write("\n".join(_device_lines(device_result, inventory)) + "\n")
        if logcat_text:
            handle.write(logcat_text)


def _write_runtime_log(log_dir: Path, args: argparse.Namespace, logcat_text: Optional[str]) -> None:
    with (log_dir / "android-runtime.log").open("w", encoding="utf-8") as handle:
        if args.status is not None:
            handle.write(f"E2E diagnostics for status {args.status}\n")
        handle.write(logcat_text or "")


def _device_lines(result: CommandResult, inventory: Inventory) -> List[str]:
    lines: List[str] = []
    if result.error:
        lines.append(f"[adb devices could not start: {result.error}]")
    if result.timed_out:
        lines.append(f"[adb devices timed out after {DEFAULT_DEVICE_TIMEOUT:g}s]")
    if result.truncated:
        lines.append(f"[adb devices output truncated at {DEVICES_BYTE_LIMIT} bytes]")
    if result.status is not None and result.status != 0:
        lines.append(f"[adb devices exited with status {result.status}]")
    body = inventory.sanitized.strip()
    if body:
        lines.append(body)
    elif not lines:
        lines.append("[no devices reported by adb]")
    return lines


def collect(args: argparse.Namespace) -> int:
    log_dir = Path(args.log_dir)
    raw_dir = Path(args.raw_dir) if args.raw_dir else None

    if args.only_file and raw_dir is None:
        print("error: --only-file requires --raw-dir", file=sys.stderr)
        return 1
    if raw_dir is not None and args.existing_safe_logs:
        print("error: --existing-safe-logs cannot be combined with --raw-dir", file=sys.stderr)
        return 1
    if raw_dir is not None:
        try:
            raw_path = raw_dir.resolve()
            output_path = log_dir.resolve()
        except OSError as exc:
            print(
                f"error: diagnostic paths could not be checked: {_safe_io_error(exc)}",
                file=sys.stderr,
            )
            return 1
        if (
            raw_path == output_path
            or raw_path in output_path.parents
            or output_path in raw_path.parents
        ):
            print("error: --raw-dir and --log-dir must be separate directories", file=sys.stderr)
            return 1

    sensitive_token = os.environ.pop("E2E_DIAGNOSTICS_SENSITIVE", "")
    device_result = run_bounded(
        [args.adb, "devices", "-l"], DEFAULT_DEVICE_TIMEOUT, DEVICES_BYTE_LIMIT
    )
    inventory = parse_inventory(device_result.output)
    redactor = Redactor(inventory.identifiers)
    redactor.add(args.serial, sensitive_token)

    logcat_text: Optional[str] = None
    if args.append_emulator or args.runtime_log:
        serial = choose_serial(inventory, args.serial)
        logcat_text = capture_logcat(
            args.adb,
            serial,
            redactor,
            args.line_limit,
            args.byte_limit,
            args.timeout,
        )

    staging_dir: Optional[Path] = None
    current_file: Optional[str] = None
    try:
        if raw_dir is not None:
            log_dir.parent.mkdir(parents=True, exist_ok=True)
            if log_dir.exists():
                if not log_dir.is_dir() or any(log_dir.iterdir()):
                    raise FileExistsError("output directory is not empty")
                log_dir.rmdir()
            staging_dir = Path(
                tempfile.mkdtemp(prefix=f".{log_dir.name}.", dir=str(log_dir.parent))
            )
            destination_dir = staging_dir
            try:
                raw_mode = raw_dir.stat().st_mode
            except FileNotFoundError:
                paths = []
            else:
                if not stat.S_ISDIR(raw_mode):
                    raise NotADirectoryError("raw log path is not a directory")
                if args.only_file:
                    paths = [raw_dir / name for name in args.only_file]
                else:
                    with os.scandir(raw_dir) as entries:
                        paths = sorted(
                            raw_dir / entry.name
                            for entry in entries
                            if entry.name.endswith(".log") and entry.is_file()
                        )
            for source in paths:
                try:
                    source_mode = source.stat().st_mode
                except FileNotFoundError:
                    continue
                if not stat.S_ISREG(source_mode):
                    continue
                current_file = source.name
                sanitize_file(
                    source,
                    destination_dir / source.name,
                    redactor,
                    args.file_limit,
                )
            current_file = None
        else:
            log_dir.mkdir(parents=True, exist_ok=True)
            if not args.existing_safe_logs and any(log_dir.glob("*.log")):
                raise FileExistsError("existing logs require --raw-dir or --existing-safe-logs")
            destination_dir = log_dir

        if args.append_emulator:
            _write_emulator_log(
                destination_dir, redactor, args, device_result, inventory, logcat_text
            )
        if args.runtime_log:
            _write_runtime_log(destination_dir, args, logcat_text)

        if staging_dir is not None:
            os.replace(staging_dir, log_dir)
            staging_dir = None
    except OSError as exc:
        subject = (
            f"could not sanitize {current_file}"
            if current_file
            else "diagnostic output or publication"
        )
        print(f"error: {subject} failed: {_safe_io_error(exc)}", file=sys.stderr)
        return 1
    finally:
        if staging_dir is not None:
            try:
                shutil.rmtree(staging_dir)
            except OSError:
                pass

    print(
        f"E2E diagnostics: {len(inventory.entries)} device(s) visible, "
        f"safe logs written to {log_dir}"
    )
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Bound and sanitize Caparaó E2E diagnostics.")
    subparsers = parser.add_subparsers(dest="command", required=True)
    collect_parser = subparsers.add_parser("collect", help="collect and sanitize E2E diagnostics")
    collect_parser.add_argument("--log-dir", required=True, help="directory receiving safe logs")
    collect_parser.add_argument(
        "--raw-dir",
        help="private directory containing raw logs; files are published only after all are sanitized",
    )
    collect_parser.add_argument(
        "--existing-safe-logs",
        action="store_true",
        help="append generated diagnostics to an output directory whose existing logs are already sanitized",
    )
    collect_parser.add_argument("--adb", required=True, help="path to adb")
    collect_parser.add_argument("--serial", default="", help="device serial to use for logcat")
    collect_parser.add_argument("--status", type=int, help="E2E exit status to record")
    collect_parser.add_argument("--heading", default="E2E diagnostics", help="heading for emulator.log")
    collect_parser.add_argument(
        "--append-emulator",
        action="store_true",
        help="append device state and AndroidRuntime errors to emulator.log",
    )
    collect_parser.add_argument(
        "--runtime-log",
        action="store_true",
        help="write AndroidRuntime errors to android-runtime.log",
    )
    collect_parser.add_argument(
        "--only-file",
        action="append",
        default=[],
        help="sanitize only this file from raw-dir (repeatable; default: every *.log)",
    )
    collect_parser.add_argument("--line-limit", type=int, default=DEFAULT_LINE_LIMIT)
    collect_parser.add_argument("--byte-limit", type=int, default=DEFAULT_BYTE_LIMIT)
    collect_parser.add_argument("--file-limit", type=int, default=DEFAULT_FILE_LIMIT)
    collect_parser.add_argument(
        "--timeout",
        type=float,
        default=DEFAULT_LOGCAT_TIMEOUT,
        help="seconds before adb logcat is killed",
    )
    return parser


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = build_parser().parse_args(argv)
    if args.command == "collect":
        return collect(args)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
