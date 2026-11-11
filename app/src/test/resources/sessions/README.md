# Recorded sessions

Real Timemore Dot sessions captured with the removed stage 0 probe or with
[`scripts/capture/capture-session.py`](../../../../scripts/capture/capture-session.py), used as test fixtures
([ADR 0010](https://github.com/MateuxLucax/caparao/issues/94)). The version 2 format has a metadata header, then
`{"t_ms", "seq", "dir": "rx"|"tx"|"event", "hex", "note"}` rows and a completion footer. `rx` lines are notifications
exactly as received, so one line may hold several frames. No MAC address or device name is recorded. Raw captures stay
in the ignored `spike-captures/` until reviewed.

| File | Phone | What happened |
|------|-------|---------------|
| `press-ramp.jsonl` | SM-S916B, Android 17 | Connect and handshake; ~34 s empty; pressing the platform harder by hand up to ~1300 g (flow saturates at 99.9, overload flag on), release back to 0. Battery pushed twice as `01 05`. |
| `idle-5min.jsonl` | SM-S916B, Android 17 | Connect and handshake; 5 min 42 s with nothing on the scale and no commands sent: no drop, no gap over 237 ms, no battery push. |
| `press-spoon-lift-removal.jsonl` | Galaxy S23, Android 17 | Trimmed to 225–585 s (arming to Done): free press, a spoon on the bloom at 447 s, a lift and return at 518 s, the press removed at 569 s; Done at 583 s. For the French press water (#214). |
| `real-filter-guided-lift-end.jsonl` | Galaxy S23, Android 17 | A guided filter from `brew_armed` at 445.5 s: three pours to 148.4 g, settled from ~503 s, dripper lifted at ~572.9 s and left off. Cut before the Done tap at 578.7 s. Replayed by `ReplayedFilterTest` (the brew ends at the lift). |

| `synthetic-espresso.jsonl` | none | **Synthetic**, written by `scripts/fixtures/synthetic-session.py`: cup placed at 2 s, a 26 s shot to 36 g from 14 s, cup lifted at 49 s. For replaying a whole espresso on the emulator (UI tests); never used to tune the detectors. |
| `synthetic-espresso-guide.jsonl` | none | **Synthetic**, written by `scripts/fixtures/synthetic-guide-sessions.py`: empty for 8 s, cup at 8 s, a 25 s shot from 18 s. For the brew guide tests. |
| `synthetic-filter-lift.jsonl` | none | **Synthetic**, written by `scripts/fixtures/synthetic-guide-sessions.py`: a 255 g pour over dripper and server from 20 s, everything lifted at 90 s. For the filter tests. |

## Prometheus-derived partial regression curves

The compact JSON files `p13-espresso-preinfusion.json`, `p18-espresso.json`, `p9-filter.json`,
`p11-filter-three-pour.json`, `p14-filter-five-pour.json` and `p16-filter-handling.json` retain every sampled
Prometheus weight and its relative timestamp from the user-reviewed cases in
[issue #79](https://github.com/MateuxLucax/caparao/issues/79#issuecomment-6618425468). They were copied from the
range-vector result without resampling or thinning; each file records its `source_brew_id`, `original_point_count`,
mode, user classification and whether the first retained sample is the first drop (espresso) or first water (filter).
The reviewed source behind #79 is kept outside the repository; the version used here has SHA-256
`76a230ace9e2644b8d43b2fec456870d259ad982a9d248832596fea814691800`.

These are partial weight/time curves, not raw BLE protocol captures: they have no original pre-detection samples,
cup placement, tare, scale commands or readings after the saved end. Tests initialize the detector with an explicit
synthetic READY state (an armed espresso detector, or zero readings that complete the filter tare) and then replay
the untouched curve. That setup is test initialization, not a recorded prelude, and it does not claim to validate cup
detection, tare or brew start.

Assertions cover normal sample processing and internal consistency: every selected point reaches the detector, the
preinfusion ramp and multi-pour waits do not end a normal brew early, and `BrewSummary` retains every in-brew sample
it was fed and agrees with the detector result. Prometheus summaries are detector outputs, not independent ground
truth for timing or numerical accuracy. P13's slow initial rise is retained as legitimate preinfusion evidence. P12's
deliberate test is excluded.

P16 is the handling case: the user touched the scale near the end, producing the 267.2 g peak, then removed the
filter, so the last readings fall to -283.8 g. The saved 0 g summary came from the old code (before the fix in #3)
saving that negative tail and the exporter clamping it; the current detector ends at the drop and keeps the observed
maximum. The true water
mass is not independently known, so the regression only checks that the negative tail is not the saved yield and
that the result is at least the weight observed before removal. It is not a target for tuning.

