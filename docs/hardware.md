# Timemore Dot BLE protocol

What the app relies on, gathered from the sources below. Items marked **to confirm** are answered by the stage 0 spike
against the real scale.

Sources: [TIMEMORE open-scale-protocol](https://github.com/TIMEMORE-COFFEE/open-scale-protocol),
[arn4v/timemore-dot-reverse-eng](https://github.com/arn4v/timemore-dot-reverse-eng),
[Beanconqueror](https://github.com/graphefruit/Beanconqueror/blob/master/src/classes/devices/timemoreDotScale.ts),
[hfsoares/dotscale](https://github.com/hfsoares/dotscale), [DrSkyFly/Pourista](https://github.com/DrSkyFly/Pourista).

## Device

- Model `TES017`, advertised name `TIMEMORE_Dot`. Match the name or model only, never a bare "dot".
- No display: one button and an LED. Powers off after about a minute idle when not connected.
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
| `01 01`, 9 bytes | notify, ~10 Hz | weight int32 BE ÷10 g, flow int16 BE ÷10 g/s, timer uint16 s, overload flag |
| `02 05` | read | battery: `[bars, percent]`; not pushed on its own, so poll every 60 s |
| `03 0D`, no payload | write | tare: `A5 5A 03 0D 00 00 64 D1` |
| `03 02 [01\|02\|03]` | write | timer start / pause / reset |
| `03 06 [00]` | write | unit grams |
| `03 08 [01 00]` | write | standard mode (02 pour-over and 03 espresso exist; the app does not use them) |
| `02 13` | read | model, answers `TES017` |

Acknowledgements come back as `03 <cmd> 00 01 01`.

## Known quirks

- The scale's flow value saturates on sudden loads (99.9 seen): clamp to 0–25 g/s and smooth.
- Weight on the platform at power-on reads negative once removed (the Dot zeroes itself at boot).
- No stability flag: stability is computed in the app.

## To confirm (stage 0)

| Question | Why it matters | Answer |
|----------|----------------|--------|
| Is a keep-alive needed? (Some apps poll every 10 s.) | Connection stability | — |
| Does the physical button send a notification? | Reflecting a tare done on the scale | — |
| Can the scale's flow go negative? | Flow clamping | — |
| Does it power off while connected and idle? | Reconnect behavior | — |
