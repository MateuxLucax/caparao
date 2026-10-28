# 19. Guided brew: a pure step tracker driven by the readings

Status: Proposed

## Context

The goal asks for a step-by-step guide during the brew: bloom, pours by target weight or time, alerts (visual, sound or
vibration) and whether the user is ahead or behind the target. The filter mode today is a free pour
([ADR 0006](0006-brew-detection-in-app.md)): live weight, flow, time and ratio, nothing to follow.

## Decision

- A pure `StepTracker` in `brew/`, next to the detectors: input is the recipe's steps ([ADR 0018](0018-recipes.md))
  and each `ScaleReading` after the brew started; output is the current step, progress in the step, the next target and
  a pace value. No Android types, tested with the replay sessions and synthetic curves like the detectors.
- A step is done when its target weight is reached (within 2 g) or, for `WAIT`/`SWIRL`, when its time has passed; the
  tracker then moves to the next one. Pouring past a target never blocks: the tracker follows the scale.
- Pace is the difference between the weight on the scale and the weight the recipe expects at this moment
  (linear between targets), shown as "ahead / on time / behind" with the grams. A ±5 g band counts as on time.
- Alerts on step changes and on "next pour in 5 s": a short vibration (`VIBRATE` permission, `VibratorManager`) and an
  optional tone (`ToneGenerator`, no audio files). Both are toggles in Settings, vibration on by default, sound off.
  The visual cue (current step card, progress ring) is always on.
- Espresso is guided only by its target window: the brew screen shows yield and time against the target and colours
  them when inside or outside it. The automatic start and end stay unchanged.
- The end of a guided filter brew is still the filter detector (done button or dripper lifted); finishing every step
  does not end the brew, because drawdown continues after the last pour.

## Consequences

- The guide is a function of the readings, so it works the same with `ReplayScaleSource` on the emulator and in
  screenshots.
- Thresholds (2 g, ±5 g, 5 s) are first guesses to be tuned with real brews, like the detector thresholds.
- `VIBRATE` is a normal permission, granted at install; no new prompt.
- Saving the per-step actual times is left out for now; the curve already holds them and the detail chart can mark the
  steps later.
