# Timemore Dot BLE protocol

What the app relies on, gathered from the sources below. Items marked **to confirm** are answered by the stage 0 spike
against the real scale.

Sources: [TIMEMORE open-scale-protocol](https://github.com/TIMEMORE-COFFEE/open-scale-protocol),
[arn4v/timemore-dot-reverse-eng](https://github.com/arn4v/timemore-dot-reverse-eng),
[Beanconqueror](https://github.com/graphefruit/Beanconqueror/blob/master/src/classes/devices/timemoreDotScale.ts),
[hfsoares/dotscale](https://github.com/hfsoares/dotscale), [DrSkyFly/Pourista](https://github.com/DrSkyFly/Pourista).

## Device

- Model `TES017`, advertised name `TIMEMORE_Dot`. Match the name or model only, never a bare "dot".
- No display: one button (power, under the scale) and an LED. Powers off after about a minute idle when not connected.
- Remembers **one** bonded device and drops other connections (HCI 0x3E). To pair another phone, hold the button until
  the LED blinks fast.

## GATT

| | UUID |
|---|---|
| Service | `0000fff0-0000-1000-8000-00805f9b34fb` |
| Notify (scale → app) | `0000fff1-0000-1000-8000-00805f9b34fb` |
| Write without response (app → scale) | `0000fff2-0000-1000-8000-00805f9b34fb` |

Connection: connect (retry up to 3 times, ~500 ms apart) → request MTU 247 → bond (required: the Dot streams only on an
encrypted link) → enable notifications on FFF1 → wait 500 ms → unit grams (`03 06 [00]`) → wait 200 ms → standard mode
(`03 08 [01 00]`) → battery query (`02 05`).

## Frames

```
A5 5A <type> <cmd> <len_hi> <len_lo> <payload…> <crc_hi> <crc_lo>
```

- `type`: `01` notification, `02` read, `03` write (and its acknowledgement).
- Outgoing CRC: CRC-16/MODBUS (init `0xFFFF`, reflected poly `0xA001`) over every byte before it, big-endian.
- Incoming frames carry CRC `00 00`: never validate it.
- One notification may hold several frames, and a frame may be split across two: buffer, resync on `A5 5A`, drop any
  declared length above 64.

| Frame | Direction | Meaning |
|-------|-----------|---------|
| `01 01`, 9 bytes | notify, ~8.4 Hz (median 119 ms, measured) | weight int32 BE ÷10 g, flow int16 BE ÷10 g/s, timer uint16 s, overload flag |
| `02 05` | read | battery: `[bars, percent]` (measured: `03 64` = 3 bars, 100 %) |
| `01 05` | notify | the same battery payload, pushed by the Dot about every 30 s (measured) |
| `03 0D`, no payload | write | tare: `A5 5A 03 0D 00 00 64 D1` |
| `03 02 [01\|02\|03]` | write | timer start / pause / reset |
| `03 06 [00]` | write | unit grams |
| `03 08 [01 00]` | write | standard mode (02 pour-over and 03 espresso exist; the app does not use them) |
| `02 13` | read | model, answers `TES017` |

Acknowledgements come back as `03 <cmd> 00 01 01`.

## Known quirks

- The scale's flow value saturates on sudden loads (99.9 measured): clamp to 0–25 g/s and smooth.
- The overload flag (payload byte 8) turned on around 1300 g (measured).
- Weight on the platform at power-on reads negative once removed (the Dot zeroes itself at boot).
- No stability flag: stability is computed in the app.

## To confirm (stage 0)

| Question | Why it matters | Answer |
|----------|----------------|--------|
| Is a keep-alive needed? (Some apps poll every 10 s.) | Connection stability | Not within 50 s: the stream ran with no command sent (press ramp session). The 5-minute idle session settles it. |
| Does the physical button send a notification? | Reflecting a tare done on the scale | Not applicable: the only button (power) is under the scale, so nobody presses it mid-brew. Tare and timer always come from the app; the button is only for power and re-pairing. |
| Can the scale's flow go negative? | Flow clamping | No: releasing 1300 g to 0 brought flow to 0.0, never below (press ramp session). |
| Does it power off while connected and idle? | Reconnect behavior | — |

### Confirmed on the device (SM-S916B, Android 17, 2026-10-25)

- Connection sequence above works as written: connected in ~0.5 s, MTU 247 accepted, services found, notifications on.
- Acknowledgements are `03 <cmd> 00 01 01` (unit and mode writes).
- The battery is both a read reply (`02 05`) and an unsolicited push (`01 05`), so polling is a fallback, not a need.
