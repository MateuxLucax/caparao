# Recorded sessions

Real Timemore Dot sessions captured with the stage 0 probe ([docs/spike.md](../../../../../docs/spike.md)), used as
test fixtures ([ADR 0010](../../../../../docs/adr/0010-testing-strategy.md)). One JSON object per line: a header, then
`{"t_ms", "dir": "rx"|"tx"|"event", "hex", "note"}`. `rx` lines are notifications exactly as received, so one line may
hold several frames. No MAC address or device name is recorded.

| File | Phone | What happened |
|------|-------|---------------|
| `press-ramp.jsonl` | SM-S916B, Android 17 | Connect and handshake; ~34 s empty; pressing the platform harder by hand up to ~1300 g (flow saturates at 99.9, overload flag on), release back to 0. Battery pushed twice as `01 05`. |
| `idle-5min.jsonl` | SM-S916B, Android 17 | Connect and handshake; 5 min 42 s with nothing on the scale and no commands sent: no drop, no gap over 237 ms, no battery push. |

Real brews (espresso, filter) are still to be recorded.
