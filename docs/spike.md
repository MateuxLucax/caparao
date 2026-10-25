# Stage 0 hardware spike

A throwaway probe (`:probe` module, not shipped) that talks to the real Dot with the platform `BluetoothGatt` API and
records every frame to JSONL. The recordings become the test fixtures ([ADR 0010](adr/0010-testing-strategy.md)).

## Install

Phone connected with USB debugging (or wireless), Bluetooth on:

```bash
./gradlew :probe:installDebug
```

The first Connect asks for "Nearby devices" permission. The Dot remembers one bonded phone: if it was paired with
another phone, hold its button until the LED blinks fast, then Connect and accept the pairing dialog.

## Recording

1. Type a label (e.g. `espresso 18g`), tap **Start session**, then **Connect**. Starting first captures the handshake
   too. The screen stays on while recording.
2. Do the scenario, then **Stop session** and **Disconnect**.
3. One file per session: `<yyyyMMdd-HHmmss>-<label>.jsonl`, first line a header, then
   `{"t_ms", "dir": "rx"|"tx"|"event", "hex", "note"}`. The MAC address and device name are never written.

Sessions to record ([roadmap](roadmap.md), stage 0):

| # | Session | Notes |
|---|---------|-------|
| 1 | Tare | Cup on, tare, add and remove weight |
| 2 | Espresso 1 | Timer start/stop around a real shot |
| 3 | Espresso 2 | |
| 4 | Filter 1 | Free pour |
| 5 | Filter 2 | |
| 6 | Cup lifted mid-brew | Lift the cup during a pour, put it back |
| 7 | Physical button | Press (short, long) with nothing else going on |
| 8 | 5 minutes idle | Connected, nothing touched, screen on |

Write a short note per session (what you did, when) next to the file: the fixtures need it.

## Pull

```bash
scripts/pull-sessions.sh   # adb pull into spike-captures/ (gitignored)
```

Check the files for identifiers before copying any to `app/src/test/resources/sessions/`.

## Questions to answer in [hardware.md](hardware.md)

1. Is a keep-alive needed? (Session 8: does the stream stop or the link drop without polling?)
2. Does the physical button send a notification? (Session 7)
3. Can the scale's flow go negative? (Sessions 2 to 6: look for negative flow values)
4. Does it power off while connected and idle? (Session 8)

Each answer cites the recording that proves it.
