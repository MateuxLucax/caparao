#!/usr/bin/env python3
"""Capture the scripted scale scenario checkpoints from the attached Android emulator."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from typing import Callable

ROOT = Path(__file__).resolve().parents[2]
SCENARIO_DIR = ROOT / "app/src/test/resources/scenarios"
DEFAULT_OUTPUT = ROOT / "captures/scripted-scale"
PACKAGE = "io.github.mateuxlucax.caparao.debug"
ACTIVITY = f"{PACKAGE}/io.github.mateuxlucax.caparao.MainActivity"
API_PORT = 8765
ADB = os.environ.get("ADB", str(Path.home() / "Library/Android/sdk/platform-tools/adb"))


def scenario_plan(path: Path) -> list[tuple[int, str]]:
    data = json.loads(path.read_text())
    captures = [(int(item["at_ms"]), str(item["name"])) for item in data.get("captures", [])]
    if not captures:
        raise ValueError(f"scenario has no named capture checkpoints: {path.name}")
    if any(at < 0 for at, _ in captures) or captures != sorted(captures):
        raise ValueError(f"capture times must be non-negative and ordered in {path.name}")
    if len({name for _, name in captures}) != len(captures):
        raise ValueError(f"capture names must be unique in {path.name}")
    return captures


def require_emulator(adb: str, serial: str, run: Callable[..., subprocess.CompletedProcess] = subprocess.run) -> None:
    if not serial:
        raise ValueError("ANDROID_SERIAL must identify the emulator")
    if not serial.startswith("emulator-"):
        raise ValueError("capture-scenarios.py only runs on an emulator serial")
    result = run([adb, "devices"], check=True, text=True, capture_output=True)
    devices = {line.split()[0]: line.split()[1] for line in result.stdout.splitlines()[1:] if len(line.split()) >= 2}
    if devices.get(serial) != "device":
        raise ValueError("ANDROID_SERIAL is not an attached adb device")


def parse_app_locales(output: str) -> str:
    """The locale list from `cmd locale get-app-locales`, as `set-app-locales --locales` expects it."""
    match = re.search(r"are\s+\[(.*?)]", output)
    if match is None:
        match = re.search(r"\[(.*?)]", output)
    return (match.group(1) if match is not None else output).strip()


def app_locales(
    adb: str,
    serial: str,
    package: str = PACKAGE,
    run: Callable[..., subprocess.CompletedProcess] = subprocess.run,
) -> str:
    result = run(
        [adb, "-s", serial, "shell", "cmd", "locale", "get-app-locales", package],
        check=True,
        text=True,
        capture_output=True,
    )
    return parse_app_locales(result.stdout)


def set_app_locales(
    adb: str,
    serial: str,
    locales: str,
    package: str = PACKAGE,
    run: Callable[..., subprocess.CompletedProcess] = subprocess.run,
) -> None:
    command = [adb, "-s", serial, "shell", "cmd", "locale", "set-app-locales", package]
    # Without --locales the command clears the app override and follows the device again.
    if locales:
        command += ["--locales", locales]
    run(command, check=True, text=True, capture_output=True)


def capture(path: Path, output_dir: Path, adb: str = ADB, serial: str | None = None, port: int = 18765) -> list[Path]:
    serial = serial or os.environ.get("ANDROID_SERIAL", "")
    require_emulator(adb, serial)
    output_dir.mkdir(parents=True, exist_ok=True)
    checkpoints = scenario_plan(path)
    captured: list[Path] = []

    def command(*args: str, binary: bool = False, timeout: float = 30) -> bytes | str:
        result = subprocess.run([adb, "-s", serial, *args], check=True, capture_output=True, timeout=timeout)
        return result.stdout if binary else result.stdout.decode("utf-8", errors="replace")

    def hierarchy() -> ET.Element:
        # uiautomator returns a non-zero exit and no root while a screen transition is running.
        deadline = time.monotonic() + 10
        while True:
            try:
                command("shell", "uiautomator", "dump", "/sdcard/scripted-capture-window.xml", timeout=15)
                return ET.fromstring(command("shell", "cat", "/sdcard/scripted-capture-window.xml"))
            except (subprocess.CalledProcessError, subprocess.TimeoutExpired, ET.ParseError):
                if time.monotonic() >= deadline:
                    raise
                time.sleep(0.2)

    def bounds_of(node: ET.Element) -> tuple[int, int, int, int] | None:
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        return tuple(int(part) for part in match.groups()) if match is not None else None

    def wait_for_text(value: str, *, contains: bool = False, timeout: float = 10) -> ET.Element:
        deadline = time.monotonic() + timeout
        while True:
            root = hierarchy()
            if any(value in node.get("text", "") if contains else node.get("text") == value for node in root.iter("node")):
                return root
            if time.monotonic() >= deadline:
                raise RuntimeError(f"app did not show {value!r}")
            time.sleep(0.2)

    def wait_for_any(values: tuple[str, ...], *, timeout: float = 10) -> str:
        deadline = time.monotonic() + timeout
        while True:
            root = hierarchy()
            visible = {node.get("text", "") for node in root.iter("node")}
            for value in values:
                if value in visible:
                    return value
            if time.monotonic() >= deadline:
                raise RuntimeError(f"app did not show any of {values!r}")
            time.sleep(0.2)

    def has_text(value: str) -> bool:
        return any(node.get("text") == value for node in hierarchy().iter("node"))

    def tap_text(value: str) -> None:
        root = wait_for_text(value)
        # A milestone label and a button can share a word ("Done"): the button sits lower on the screen.
        nodes = sorted(
            (node for node in root.iter("node") if node.get("text") == value),
            key=lambda node: (bounds_of(node) or (0, 0, 0, 0))[3],
            reverse=True,
        )
        # Compose puts the label on a child of the clickable control, so the text node itself can report enabled=false.
        node = next((node for node in nodes if node.get("clickable") == "true"), nodes[0])
        bounds = bounds_of(node)
        if bounds is None:
            raise RuntimeError(f"app did not expose tap bounds for {value!r}")
        left, top, right, bottom = bounds
        command("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
        time.sleep(0.35)

    def screenshot(name: str) -> Path:
        destination = output_dir / f"{path.stem}--{name}.png"
        destination.write_bytes(command("exec-out", "screencap", "-p", binary=True))
        captured.append(destination)
        return destination

    subprocess.run([adb, "-s", serial, "forward", "--remove", f"tcp:{port}"], check=False, capture_output=True)
    command("forward", f"tcp:{port}", f"tcp:{API_PORT}")
    # A restored brew screen would otherwise outlive the force stop: NEW_TASK | CLEAR_TASK starts at Home every run.
    command("shell", "am", "start", "-S", "-n", ACTIVITY, "-f", "0x10008000", "--ez", "scripted_api", "true", "--ez", "script_paused", "true")

    def api(method: str, endpoint: str, payload: bytes | dict | None = None, content_type: str = "application/json") -> dict:
        data = json.dumps(payload).encode() if isinstance(payload, dict) else payload
        request = urllib.request.Request(f"http://127.0.0.1:{port}{endpoint}", data=data, method=method)
        if data is not None:
            request.add_header("Content-Type", content_type)
        try:
            with urllib.request.urlopen(request, timeout=3) as response:
                return json.loads(response.read())
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"API {method} {endpoint} returned {error.code}: {detail}") from error

    def advance_clock(delta_ms: int) -> None:
        remaining = delta_ms
        while remaining > 0:
            step = min(20, remaining)
            api("POST", "/clock", {"action": "advance", "ms": step})
            time.sleep(0.03)
            remaining -= step

    def manual_reading() -> None:
        # Home looks alive in the README: a steady 3.2 g/s ramp instead of an idle 0.0 g.
        for delta_ms, grams in ((1000, 10.0), (500, 11.6), (500, 13.2)):
            advance_clock(delta_ms)
            api("POST", "/reading", {"weight_g": grams})
        time.sleep(0.6)

    def settle_tare() -> None:
        # Ready arms the filter or press detector; the scenario load below resets the scale, so the steady zero that
        # confirms the tare has to happen first. The first reading asks for the tare, the next two hold zero (#306).
        for _ in range(3):
            api("POST", "/reading", {"weight_g": 0})
            advance_clock(600)
        time.sleep(0.2)

    try:
        deadline = time.monotonic() + 10
        while True:
            try:
                api("GET", "/state")
                break
            except (OSError, urllib.error.URLError, json.JSONDecodeError):
                if time.monotonic() >= deadline:
                    raise RuntimeError("scripted API did not start")
                time.sleep(0.1)
        ready_deadline = time.monotonic() + 15
        while True:
            root = hierarchy()
            visible = {node.get("text", "") for node in root.iter("node")}
            if "Home" in visible and "Caparaó" in visible:
                break
            if time.monotonic() >= ready_deadline:
                raise RuntimeError("app did not reach its Home screen")
            time.sleep(0.2)

        scenario = json.loads(path.read_text())
        mode_label = {"filter": "Filter", "filter-spoon": "Filter", "espresso": "Espresso", "french-press": "Press"}.get(path.stem)
        if mode_label is not None:
            api("POST", "/event", {"event": "connected"})
            manual_reading()
            screenshot("home")
            tap_text(mode_label)
            # Let the mode's first screen settle before touching a recipe chip.
            wait_for_text("Next")
            if path.stem == "espresso":
                tap_text("Free brew")
            elif path.stem == "filter":
                # Filter opens on a free brew; choose the built-in that matches the scenario for the guided flow.
                tap_text("Melitta 102")
                wait_for_text("1:15", contains=True)
            screenshot("dose")
            tap_text("Next")
            if wait_for_any(("Which coffee?", "Weigh the beans")) == "Which coffee?":
                tap_text("No bag")
                tap_text("Next")
                wait_for_text("Weigh the beans")
            dose = float(scenario.get("dose_g", 18))
            api("POST", "/reading", {"weight_g": dose})
            time.sleep(0.35)
            screenshot("weigh-beans")
            api("POST", "/reading", {"weight_g": 0})
            tap_text("Next")
            if path.stem == "espresso":
                wait_for_text("Prepare the portafilter")
                screenshot("prepare-portafilter")
                # The cup is already on the scale when Ready arms the detector, so it tares from that reading.
                api("POST", "/scenario", path.read_bytes())
                api("POST", "/clock", {"action": "pause"})
                advance_clock(2100)
                tap_text("Ready")
                wait_for_text("Put the cup on the scale")
                # The cup is already there: arm from that reading instead of waiting for the steady-cup tare.
                tap_text("Cup's on")
                # The cup's own readings become zero after the tare offset; let the scenario feed them so the tare
                # completes and the shot screen opens before the checkpoints start.
                advance_clock(1000)
                wait_for_text("Start the shot")
                time.sleep(0.5)
            elif path.stem in ("filter", "filter-spoon"):
                wait_for_text("Prepare the filter")
                screenshot("prepare-filter")
                tap_text("Ready")
                settle_tare()
                api("POST", "/scenario", path.read_bytes())
            else:
                wait_for_text("Prepare the press")
                screenshot("prepare-french-press")
                tap_text("Ready")
                settle_tare()
                api("POST", "/scenario", path.read_bytes())
        else:
            api("POST", "/scenario", path.read_bytes())

        api("POST", "/clock", {"action": "pause"})
        previous_ms = 2100 if path.stem == "espresso" else 0
        for at_ms, name in checkpoints:
            if at_ms < previous_ms:
                continue
            advance_clock(at_ms - previous_ms)
            time.sleep(0.8)
            if name == "summary" and path.stem in ("filter", "filter-spoon") and not has_text("Saved"):
                # A lifted dripper can end the brew already; only finish it from the paused screen.
                tap_text("Done")
            if name == "finished" or (path.stem in ("filter", "filter-spoon") and name == "summary"):
                wait_for_text("Saved", timeout=10)
            screenshot(name)
            previous_ms = at_ms
        if path.stem == "french-press":
            wait_for_text("Press and serve", contains=True)
            tap_text("Done")
            wait_for_text("Saved", timeout=10)
            screenshot("summary")
    finally:
        subprocess.run([adb, "-s", serial, "forward", "--remove", f"tcp:{port}"], check=False, capture_output=True)
    return captured


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scenario", choices=[p.stem for p in sorted(SCENARIO_DIR.glob("*.json"))] + ["all"], default="all")
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--port", type=int, default=18765, help="host port for adb forward")
    args = parser.parse_args()
    serial = os.environ.get("ANDROID_SERIAL", "")
    output_dir = args.output_dir if args.output_dir.is_absolute() else ROOT / args.output_dir
    previous_locales: str | None = None
    restore_failed = False
    try:
        require_emulator(ADB, serial)
        # The script matches English labels; put the app in English for the run and put back what it had afterwards.
        previous_locales = app_locales(ADB, serial)
        set_app_locales(ADB, serial, "en-US")
        scenarios = sorted(SCENARIO_DIR.glob("*.json")) if args.scenario == "all" else [SCENARIO_DIR / f"{args.scenario}.json"]
        files = [image for scenario in scenarios for image in capture(scenario, output_dir, ADB, serial, args.port)]
    except (OSError, ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        message = "adb command failed" if isinstance(error, subprocess.CalledProcessError) else str(error)
        print(f"capture failed: {type(error).__name__}: {message}", file=sys.stderr)
        return 1
    finally:
        if previous_locales is not None:
            try:
                set_app_locales(ADB, serial, previous_locales)
            except (OSError, subprocess.CalledProcessError) as error:
                print(f"could not restore the app locale: {error}", file=sys.stderr)
                restore_failed = True
    if restore_failed:
        return 1
    print(f"Captured {len(files)} states in {output_dir.relative_to(ROOT)}")
    for path in files:
        print(f"{path.relative_to(ROOT)} ({path.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
