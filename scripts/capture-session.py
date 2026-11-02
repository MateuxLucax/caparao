#!/usr/bin/env python3
"""Records a Timemore Dot BLE session as JSONL compatible with ReplayScaleSource.

The script scans for the Dot, runs the application-level handshake from docs/hardware.md and then records every FFF1
notification until Ctrl+C. It owns the BLE link while running, so the app must be disconnected.

Privacy is part of the format: this repository is public. The scale's address and advertised names are used to scan
and connect but are never written; labels and free-form notes are redacted before they reach the file.
"""
from __future__ import annotations

import argparse
import asyncio
import json
import re
import signal
import sys
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Optional, Sequence

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_CAPTURE_DIR = REPO_ROOT / "spike-captures"

DOT_NAME = "TIMEMORE_Dot"
DOT_MODEL = "TES017"
NOTIFY_UUID = "0000fff1-0000-1000-8000-00805f9b34fb"
WRITE_UUID = "0000fff2-0000-1000-8000-00805f9b34fb"

UNIT_GRAMS = bytes.fromhex("A5 5A 03 06 00 01 00 E8 A7")
STANDARD_MODE = bytes.fromhex("A5 5A 03 08 00 02 01 00 EB 31")
READ_BATTERY = bytes.fromhex("A5 5A 02 05 00 00 5A 51")

SCAN_TIMEOUT_SECONDS = 10.0
CONNECT_ATTEMPTS = 3
CONNECT_RETRY_SECONDS = 0.5
NOTIFY_DELAY_SECONDS = 0.5
MODE_DELAY_SECONDS = 0.2

APP_NAME = "capture-session.py"
REDACTED = "[redacted]"

ScannerFactory = Callable[[Callable[[Any, Any], None]], Any]
ClientFactory = Callable[[Any, Callable[[Any], None]], Any]
Sleep = Callable[[float], Any]

_MAC = re.compile(r"(?<![0-9A-Fa-f])(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}(?![0-9A-Fa-f])")
_COMPACT_MAC = re.compile(r"(?<![0-9A-Fa-f])[0-9A-Fa-f]{12}(?![0-9A-Fa-f])")
_UUID = re.compile(
    r"(?<![0-9A-Fa-f])[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-"
    r"[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}(?![0-9A-Fa-f])",
)


@dataclass(frozen=True)
class CaptureResult:
    path: Path
    exit_code: int


@dataclass(frozen=True)
class DetectedDot:
    device: Any
    identifiers: tuple[str, ...]


def is_dot_name(name: Optional[str]) -> bool:
    """The same match as AndroidBleTransport: the exact advertised name or the model substring, never bare "dot"."""
    return name == DOT_NAME or (name is not None and DOT_MODEL in name)


class Redactor:
    """Removes known device identifiers and identifier-shaped text from labels, notes and safe errors."""

    def __init__(self, sensitive: Sequence[Optional[str]] = (DOT_NAME, DOT_MODEL)) -> None:
        self._sensitive: list[str] = []
        self.add(*sensitive)

    def add(self, *values: Optional[str]) -> None:
        for value in values:
            if value and value not in self._sensitive:
                self._sensitive.append(value)
        self._sensitive.sort(key=len, reverse=True)

    def redact(self, text: str) -> str:
        result = str(text)
        for value in self._sensitive:
            result = re.sub(re.escape(value), REDACTED, result, flags=re.IGNORECASE)
        result = _MAC.sub(REDACTED, result)
        result = _UUID.sub(REDACTED, result)
        return _COMPACT_MAC.sub(REDACTED, result)


def _hex(data: bytes) -> str:
    return " ".join(f"{byte:02X}" for byte in data)


class SessionLog:
    """One JSONL file with a header followed by `t_ms`, `dir`, `hex` and `note` rows."""

    def __init__(
        self,
        label: str,
        output_dir: Path = DEFAULT_CAPTURE_DIR,
        *,
        redactor: Optional[Redactor] = None,
        wall_clock: Optional[Callable[[], datetime]] = None,
        monotonic_ns: Optional[Callable[[], int]] = None,
        started_at: Optional[datetime] = None,
        start_ns: Optional[int] = None,
        prelude_events: Sequence[tuple[int, str]] = (),
    ) -> None:
        self.redactor = redactor or Redactor()
        self._wall_clock = wall_clock or (lambda: datetime.now(timezone.utc))
        self._monotonic_ns = monotonic_ns or time.monotonic_ns
        self._start_ns = start_ns if start_ns is not None else self._monotonic_ns()
        self._last_t_ms = 0
        self._file: Optional[Any] = None

        output_dir.mkdir(parents=True, exist_ok=True)
        now = started_at or self._wall_clock()
        if now.tzinfo is None:
            now = now.replace(tzinfo=timezone.utc)
        now = now.astimezone(timezone.utc)
        stamp = now.strftime("%Y%m%d-%H%M%S")
        safe_label = self.redactor.redact(label).strip() or "session"
        slug = re.sub(r"[^a-z0-9]+", "-", safe_label.lower()).strip("-")[:60] or "session"
        self.path = output_dir / f"{stamp}-{slug}.jsonl"
        self._file = self.path.open("x", encoding="utf-8", newline="\n")
        self._write(
            {
                "label": safe_label,
                "started_at": now.isoformat().replace("+00:00", "Z"),
                "app": APP_NAME,
            }
        )
        for at_ms, note in prelude_events:
            self.event(note, at_ms=at_ms)

    @property
    def active(self) -> bool:
        return self._file is not None

    def _elapsed_ms(self) -> int:
        elapsed = max(0, (self._monotonic_ns() - self._start_ns) // 1_000_000)
        self._last_t_ms = max(self._last_t_ms, elapsed)
        return self._last_t_ms

    def _write(self, payload: dict[str, Any]) -> None:
        if self._file is None:
            raise ValueError("session log is closed")
        self._file.write(json.dumps(payload, separators=(",", ":"), ensure_ascii=False))
        self._file.write("\n")
        self._file.flush()

    def _record(self, direction: str, data: bytes, note: str, at_ms: Optional[int] = None) -> None:
        timestamp = self._elapsed_ms() if at_ms is None else max(0, at_ms)
        self._last_t_ms = max(self._last_t_ms, timestamp)
        self._write(
            {
                "t_ms": self._last_t_ms,
                "dir": direction,
                "hex": _hex(data),
                "note": self.redactor.redact(note),
            }
        )

    def event(self, note: str, at_ms: Optional[int] = None) -> None:
        self._record("event", b"", note, at_ms)

    def tx(self, frame: bytes, note: str) -> None:
        self._record("tx", frame, note)

    def rx(self, data: bytes, note: str = "notification") -> None:
        self._record("rx", data, note)

    def close(self) -> None:
        if self._file is not None:
            self._file.close()
            self._file = None


def bleak_scanner_factory(callback: Callable[[Any, Any], None]) -> Any:
    from bleak import BleakScanner

    return BleakScanner(detection_callback=callback)


def bleak_client_factory(device: Any, disconnected_callback: Callable[[Any], None]) -> Any:
    from bleak import BleakClient

    return BleakClient(
        device,
        disconnected_callback=disconnected_callback,
        pair=True,
        timeout=30.0,
    )


async def scan_for_dot(
    scanner_factory: ScannerFactory,
    timeout: float = SCAN_TIMEOUT_SECONDS,
) -> Optional[DetectedDot]:
    """Scans until the first Dot is seen and returns its identifiers without logging them."""
    found = asyncio.Event()
    matches: list[DetectedDot] = []

    def on_detection(device: Any, advertisement: Any) -> None:
        if found.is_set():
            return
        name = getattr(device, "name", None)
        local_name = getattr(advertisement, "local_name", None)
        if not is_dot_name(name) and not is_dot_name(local_name):
            return
        identifiers = tuple(
            value
            for value in (name, local_name, getattr(device, "address", None))
            if value
        )
        matches.append(DetectedDot(device, identifiers))
        found.set()

    async with scanner_factory(on_detection):
        try:
            await asyncio.wait_for(found.wait(), timeout)
        except asyncio.TimeoutError:
            return None
    return matches[0] if matches else None


async def connect_dot(
    device: Any,
    log: SessionLog,
    disconnected_callback: Callable[[Any], None],
    client_factory: ClientFactory,
    sleep: Sleep,
) -> Optional[Any]:
    """Three connect attempts, 500 ms apart, matching the app's connection policy."""
    for attempt in range(1, CONNECT_ATTEMPTS + 1):
        log.event(f"connect_attempt {attempt}")
        client = None
        try:
            client = client_factory(device, disconnected_callback)
            await client.connect()
            return client
        except Exception as error:  # noqa: BLE001 - only the safe class name is logged
            log.event(f"connect_failed {type(error).__name__}")
            if client is not None:
                try:
                    await client.disconnect()
                except Exception as cleanup_error:  # noqa: BLE001
                    log.event(f"connect_cleanup_failed {type(cleanup_error).__name__}")
            if attempt < CONNECT_ATTEMPTS:
                await sleep(CONNECT_RETRY_SECONDS)
    return None


async def _write_frame(log: SessionLog, client: Any, frame: bytes, note: str) -> None:
    # Log before awaiting: an acknowledgement can arrive before the write future completes.
    log.tx(frame, note)
    await client.write_gatt_char(WRITE_UUID, frame, response=False)


async def _wait_for_stop(stop_event: asyncio.Event, disconnected: asyncio.Event) -> bool:
    """Returns True when the link was lost and False for a requested stop."""
    stop_task = asyncio.create_task(stop_event.wait())
    disconnected_task = asyncio.create_task(disconnected.wait())
    try:
        await asyncio.wait({stop_task, disconnected_task}, return_when=asyncio.FIRST_COMPLETED)
    finally:
        for task in (stop_task, disconnected_task):
            if not task.done():
                task.cancel()
        await asyncio.gather(stop_task, disconnected_task, return_exceptions=True)
    return disconnected.is_set()


async def capture_session(
    label: str,
    *,
    output_dir: Path = DEFAULT_CAPTURE_DIR,
    scanner_factory: Optional[ScannerFactory] = None,
    client_factory: Optional[ClientFactory] = None,
    sleep: Optional[Sleep] = None,
    stop_event: Optional[asyncio.Event] = None,
    scan_timeout: float = SCAN_TIMEOUT_SECONDS,
    wall_clock: Optional[Callable[[], datetime]] = None,
    monotonic_ns: Optional[Callable[[], int]] = None,
    on_status: Optional[Callable[[str], None]] = None,
) -> CaptureResult:
    """Captures one connection until Ctrl+C (through stop_event) or disconnection."""
    scanner = scanner_factory or bleak_scanner_factory
    client_for = client_factory or bleak_client_factory
    pause = sleep or asyncio.sleep
    stop = stop_event or asyncio.Event()
    clock = monotonic_ns or time.monotonic_ns
    started_at = wall_clock() if wall_clock is not None else datetime.now(timezone.utc)
    start_ns = clock()
    prelude_events: list[tuple[int, str]] = []
    log: Optional[SessionLog] = None
    client = None
    connected = False
    status = 1
    disconnected = asyncio.Event()

    def announce(message: str) -> None:
        if on_status is not None:
            on_status(message)

    def elapsed_ms() -> int:
        return max(0, (clock() - start_ns) // 1_000_000)

    def record_event(note: str) -> None:
        if log is None:
            prelude_events.append((elapsed_ms(), note))
        else:
            log.event(note)

    def open_log(redactor: Optional[Redactor] = None) -> SessionLog:
        nonlocal log
        log = SessionLog(
            label,
            output_dir,
            redactor=redactor,
            monotonic_ns=clock,
            started_at=started_at,
            start_ns=start_ns,
            prelude_events=tuple(prelude_events),
        )
        return log

    def on_disconnected(disconnected_client: Any) -> None:
        if disconnected_client is not client or not connected or disconnected.is_set():
            return
        if log is None or not log.active:
            return
        log.event("disconnected")
        disconnected.set()

    try:
        announce(f"scanning for {DOT_NAME} (10 s)...")
        record_event("session_start")
        record_event("scan_start")
        found = await scan_for_dot(scanner, scan_timeout)
        if found is None:
            record_event("scan_timeout")
            open_log()
            return CaptureResult(log.path, 1)

        record_event("scan_match")
        redactor = Redactor()
        redactor.add(*found.identifiers)
        open_log(redactor)
        announce(f"recording to {log.path}")

        client = await connect_dot(found.device, log, on_disconnected, client_for, pause)
        if client is None:
            record_event("connect_failed_all")
            return CaptureResult(log.path, 1)

        connected = True
        record_event("connected")
        await client.start_notify(
            NOTIFY_UUID,
            lambda _sender, data: log.rx(bytes(data)) if log.active else None,
        )
        record_event("notifications_enabled")

        await pause(NOTIFY_DELAY_SECONDS)
        await _write_frame(log, client, UNIT_GRAMS, "unit grams")
        await pause(MODE_DELAY_SECONDS)
        await _write_frame(log, client, STANDARD_MODE, "standard mode")
        await _write_frame(log, client, READ_BATTERY, "battery")
        record_event("handshake_done")
        announce("connected; recording until Ctrl+C")

        if stop.is_set():
            record_event("disconnect_requested")
            status = 0
        elif await _wait_for_stop(stop, disconnected):
            status = 1
        else:
            record_event("disconnect_requested")
            status = 0
    except asyncio.CancelledError:
        record_event("capture_cancelled")
        if log is None:
            open_log()
        status = 130
        raise
    except Exception as error:  # noqa: BLE001 - suppress tracebacks, which may carry a device representation
        record_event(f"capture_failed {type(error).__name__}")
        if log is None:
            open_log()
        status = 1
    finally:
        if log is not None:
            if client is not None:
                if connected:
                    try:
                        await client.stop_notify(NOTIFY_UUID)
                    except Exception as error:  # noqa: BLE001
                        log.event(f"stop_notify_failed {type(error).__name__}")
                try:
                    await client.disconnect()
                    if connected and not disconnected.is_set():
                        log.event("disconnected")
                        disconnected.set()
                except Exception as error:  # noqa: BLE001
                    log.event(f"disconnect_failed {type(error).__name__}")
            log.event("session_end")
            log.close()
    assert log is not None
    return CaptureResult(log.path, status)


def _install_stop_handlers(stop_event: asyncio.Event) -> None:
    loop = asyncio.get_running_loop()
    for signum in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(signum, stop_event.set)
        except (NotImplementedError, RuntimeError):
            # The KeyboardInterrupt path in main still releases the file; the link may need a manual retry.
            pass


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Record a Timemore Dot BLE session as JSONL for RecordedSessionTest.",
        epilog=(
            "Disconnect Caparaó and any other app from the scale before running. "
            "The script owns the BLE link until Ctrl+C."
        ),
    )
    parser.add_argument("label", help="short description used in the file name and header, e.g. 'espresso 18g'")
    return parser


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = _parser().parse_args(argv)

    try:
        import bleak  # noqa: F401 - fail before creating a file when the dependency is missing
    except ModuleNotFoundError:
        print(
            f"bleak is not installed; run: {sys.executable} -m pip install "
            "-r scripts/requirements-capture.txt",
            file=sys.stderr,
        )
        return 2

    async def run() -> CaptureResult:
        stop = asyncio.Event()
        _install_stop_handlers(stop)
        return await capture_session(
            args.label,
            stop_event=stop,
            on_status=lambda message: print(message, flush=True),
        )

    try:
        result = asyncio.run(run())
    except KeyboardInterrupt:
        print("capture interrupted", file=sys.stderr)
        return 130
    except Exception as error:  # noqa: BLE001 - a traceback could expose device details
        print(f"capture failed ({type(error).__name__})", file=sys.stderr)
        return 1

    if result.exit_code == 0:
        print(f"capture saved to {result.path}")
    else:
        print(f"capture incomplete; partial log kept at {result.path}", file=sys.stderr)
    return result.exit_code


if __name__ == "__main__":
    raise SystemExit(main())
