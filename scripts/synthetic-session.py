#!/usr/bin/env python3
"""Writes app/src/test/resources/sessions/synthetic-espresso.jsonl: a made-up espresso in the probe's format.

Not a recording: it exists so the emulator can replay a whole espresso (screenshots, UI tests) until real ones are
recorded. Timeline: empty scale, cup (150 g) placed at 2 s and left still until 14 s (time to set the dose), a 26 s shot to 36 g with the flow
easing off at the end, the cup left on the scale, then lifted. Weights are raw (the replay applies the app's tare).
"""
import json, math, random, pathlib

random.seed(7)
out = pathlib.Path(__file__).resolve().parent.parent / "app/src/test/resources/sessions/synthetic-espresso.jsonl"


def frame(grams, flow):
    w = round(grams * 10) & 0xFFFFFFFF
    f = round(min(flow, 99.9) * 10) & 0xFFFF
    payload = w.to_bytes(4, "big") + f.to_bytes(2, "big") + (0).to_bytes(2, "big") + b"\x00"
    data = b"\xa5\x5a\x01\x01" + len(payload).to_bytes(2, "big") + payload + b"\x00\x00"
    return " ".join(f"{b:02X}" for b in data)


def weight_at(t):
    cup = 150.0
    if t < 2.0:
        return 0.0
    if t < 2.5:
        return cup * (t - 2.0) / 0.5
    shot_start, shot_len, dose_out = 14.0, 26.0, 36.0
    if t < shot_start:
        return cup
    if t < shot_start + shot_len:
        x = (t - shot_start) / shot_len
        # Slow first drops, steady middle, tapering end.
        return cup + dose_out * (3 * x**2 - 2 * x**3) ** 0.85
    if t < 49.0:
        return cup + dose_out
    return 0.0


lines = [json.dumps({"label": "SYNTHETIC espresso (scripts/synthetic-session.py), not a recording", "app": "script"})]
prev = 0.0
for i in range(0, 520):
    t = i / 10
    g = weight_at(t) + (random.choice([-0.1, 0, 0, 0.1]) if 2.5 < t < 49 else 0)
    flow = max(0.0, (g - prev) * 10)
    prev = g
    lines.append(json.dumps({"t_ms": i * 100, "dir": "rx", "hex": frame(g, flow)}, separators=(",", ":")))
out.write_text("\n".join(lines) + "\n")
print(f"wrote {out} ({len(lines) - 1} frames)")
