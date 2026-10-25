# 6. Brew detection in the app, with the scale in standard mode

Status: Accepted

## Context

The Dot's firmware has pour-over and espresso modes that report stages (waiting for drip, running, done), but it has no
display to show them, their thresholds are not documented, and every open-source client keeps the scale in standard
mode. The app needs automatic espresso (start at the first drop, stop when the flow stops) and a free-pour filter mode.

## Decision

Put the scale in standard mode and run detection in the app, in pure classes in `brew/`
(`EspressoDetector`, `FilterSession`), fed by weight readings at ~10 Hz. Starting thresholds come from dotscale and
Pourista (stable = ≤0.3 g range in 1 s; end of espresso = ≤0.2 g rise in 4 s) and are tuned with real recordings.

## Consequences

- Behavior is ours to tune and test on the JVM with recordings from the real scale.
- The scale's own timer is used only as a manual timer on Home.
