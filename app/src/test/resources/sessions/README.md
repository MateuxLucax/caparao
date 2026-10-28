# Recorded sessions

Real Timemore Dot sessions captured with the stage 0 probe ([docs/spike.md](../../../../../docs/spike.md)), used as
test fixtures ([ADR 0010](../../../../../docs/adr/0010-testing-strategy.md)). One JSON object per line: a header, then
`{"t_ms", "dir": "rx"|"tx"|"event", "hex", "note"}`. `rx` lines are notifications exactly as received, so one line may
hold several frames. No MAC address or device name is recorded.

| File | Phone | What happened |
|------|-------|---------------|
| `press-ramp.jsonl` | SM-S916B, Android 17 | Connect and handshake; ~34 s empty; pressing the platform harder by hand up to ~1300 g (flow saturates at 99.9, overload flag on), release back to 0. Battery pushed twice as `01 05`. |
| `idle-5min.jsonl` | SM-S916B, Android 17 | Connect and handshake; 5 min 42 s with nothing on the scale and no commands sent: no drop, no gap over 237 ms, no battery push. |

| `synthetic-espresso.jsonl` | none | **Synthetic**, written by `scripts/synthetic-session.py`: cup placed at 2 s, a 26 s shot to 36 g from 14 s, cup lifted at 49 s. For replaying a whole espresso on the emulator (screenshots, UI tests); never used to tune the detectors. |
| `synthetic-espresso-guide.jsonl` | none | **Synthetic**, written by `scripts/synthetic-guide-sessions.py`: empty for 8 s, cup at 8 s, a 25 s shot from 18 s. For the brew guide screenshots. |
| `synthetic-filter-lift.jsonl` | none | **Synthetic**, written by `scripts/synthetic-guide-sessions.py`: a 255 g pour over dripper and server from 20 s, everything lifted at 90 s. For the filter screenshots. |

Real brews (espresso, filter) are still to be recorded.
