#!/usr/bin/env python3
"""Writes two made-up sessions for the brew guide screenshots (ScreenshotsTest), in the probe's format.

Not recordings, never used to tune the detectors:
- synthetic-espresso-guide.jsonl: empty scale for 8 s (time to set the dose and see the cup step), cup (150 g) placed
  at 8 s, first drop at 18 s, a 25 s shot to 36 g, the cup left on the scale.
- synthetic-filter-lift.jsonl: dripper and server (400 g) on the scale, pours from 20 s (bloom to 50 g, then 100,
  150 and 255 g), everything lifted off the scale at 90 s.
Weights are raw (the replay applies the app's tare).
"""
import json, random, pathlib

sessions = pathlib.Path(__file__).resolve().parent.parent / "app/src/test/resources/sessions"


def frame(grams, flow):
    w = round(grams * 10) & 0xFFFFFFFF
    f = round(min(max(flow, 0.0), 99.9) * 10) & 0xFFFF
    payload = w.to_bytes(4, "big") + f.to_bytes(2, "big") + (0).to_bytes(2, "big") + b"\x00"
    data = b"\xa5\x5a\x01\x01" + len(payload).to_bytes(2, "big") + payload + b"\x00\x00"
    return " ".join(f"{b:02X}" for b in data)


def espresso(t):
    cup = 150.0
    if t < 8.0:
        return 0.0
    if t < 8.5:
        return cup * (t - 8.0) / 0.5
    start, length, out = 18.0, 25.0, 36.0
    if t < start:
        return cup
    x = min((t - start) / length, 1.0)
    return cup + out * (3 * x**2 - 2 * x**3) ** 0.85


POURS = [(20, 28, 0, 50), (55, 63, 50, 100), (70, 78, 100, 150), (80, 88, 150, 255)]


def filter_lift(t):
    if t >= 90:
        return 0.0
    water = 0.0
    for start, end, low, high in POURS:
        if t >= end:
            water = high
        elif t >= start:
            water = low + (high - low) * (t - start) / (end - start)
    return 400.0 + water


def write(name, label, weight_at, seconds, noisy):
    random.seed(7)
    lines = [json.dumps({"label": label, "app": "script"})]
    prev = weight_at(0)
    for i in range(seconds * 10):
        g = weight_at(i / 10)
        if noisy(i / 10, g):
            g += random.choice([-0.1, 0, 0, 0.1])
        lines.append(json.dumps({"t_ms": i * 100, "dir": "rx", "hex": frame(g, (g - prev) * 10)}, separators=(",", ":")))
        prev = g
    (sessions / name).write_text("\n".join(lines) + "\n")
    print(f"wrote {name} ({len(lines) - 1} frames)")


write("synthetic-espresso-guide.jsonl", "SYNTHETIC espresso for the guide screenshots, not a recording", espresso, 60,
      lambda t, g: t > 8.5)
write("synthetic-filter-lift.jsonl", "SYNTHETIC filter with the server lifted, not a recording", filter_lift, 110,
      lambda t, g: g > 0)
