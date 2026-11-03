# Recording a Dot session

Caparaó can record a local diagnostic session while it uses its existing BLE connection. Use this for annotated everyday
brews; the file includes the pre-brew scale baseline, each raw notification chunk, successful commands, safe connection
events and brew detector phases. Start recording in **Settings → Diagnostics**, brew normally, then return to Settings
and stop recording. Sharing is a separate action from the finished session.

The recorder is off by default. It writes to app-private storage, outside Android backups, and never sends data to the
exporter or another service. A file leaves the app only after the user chooses **Share file**. Recording does not change
scale readings or wait for disk writes on the BLE or brew-detection path.

Retention is bounded to 10 sessions and 32 MiB total; each session stops at 8 MiB. Before a new session, the oldest
files are removed as needed to reserve room. The asynchronous writer holds at most 128 records. A full queue, size cap,
write failure or lost BLE link ends the capture as incomplete with a fixed reason in its footer when storage still works.
The app continues brewing if recording stops. A file without a completion footer is treated as incomplete after the
app process ends unexpectedly.

## In-app format

In-app files use version 1 of `caparao-diagnostic-jsonl`. They keep the same first-line header and `rx`/`tx`/`event`
records used by `ReplayScaleSource`; existing replay ignores the extra `seq`, `complete` and `reason` fields.

```json
{"format":"caparao-diagnostic-jsonl","format_version":1,"app":"Caparaó","started_at":"2026-11-03T20:00:00Z"}
{"t_ms":0,"seq":0,"dir":"event","hex":"","note":"session_started"}
{"t_ms":245,"seq":1,"dir":"rx","hex":"A5 5A 01 01 00 09 00 00 00 00 00 00 00 00 00 00 00","note":"notification"}
{"t_ms":246,"seq":2,"dir":"tx","hex":"A5 5A 03 0D 00 00 64 D1","note":"command"}
{"t_ms":800,"seq":3,"dir":"event","hex":"","note":"brew_espresso_brewing"}
{"t_ms":900,"seq":4,"dir":"event","hex":"","note":"session_end","complete":true}
```

`t_ms` is elapsed monotonic time from capture start. `seq` preserves order when two records share a millisecond. Each
`rx` row is one original BLE notification, including chunks that contain several frames or a partial frame; `tx` rows
contain only successful writes. Event notes come from a fixed vocabulary and contain no recipe, coffee, device or error
text. The header has no device label. A disconnected or interrupted session ends with `session_incomplete`,
`complete:false` and a fixed `reason`; successful files end with `session_end`, `complete:true`.

Complete in-app files can be used directly with `ReplayScaleSource` or `RecordedSessionTest`: preserve the first header
line and all rows, and let replay consume only `rx`. Review a file before promoting it to a public fixture. Incomplete
files are evidence of a capture failure or disconnect and must not be presented as a complete session.

## External protocol capture

[`scripts/capture-session.py`](../scripts/capture-session.py) remains available for protocol work. It scans for the
Timemore Dot, runs the handshake from [hardware.md](hardware.md) and records every FFF1 notification until Ctrl+C. It
uses the same replay-compatible JSONL rows, and its raw files stay in the git-ignored `spike-captures/` until reviewed.

The external script owns the BLE link. Caparaó and every other app must be disconnected while it runs. The Dot also
remembers one bonded device: connecting from the Mac may require holding the Dot's button until its LED blinks fast, and
after the capture the phone may need to pair again.

## Setup (macOS)

Python 3.10 or newer is required by Bleak. Check the default interpreter, then use an isolated environment in the
repository:

```bash
python3 --version  # must be 3.10 or newer
python3 -m venv .venv
.venv/bin/python -m pip install -r scripts/requirements-capture.txt
```

If the default `python3` is older, create the environment with a newer interpreter instead, for example
`/opt/homebrew/bin/python3.15 -m venv .venv` or `uv venv --python 3.12 .venv` (remove an older `.venv` first). Only macOS
has been validated; the code uses Bleak and may work on other desktop platforms, but their pairing and permission
behavior is untested here. Allow Terminal or its Python process Bluetooth access when macOS asks.

## Record

1. Close or force-stop Caparaó so it cannot reconnect first. Close the Timemore app and any other BLE client too.
2. Turn the Dot on. If it is bonded to the phone, hold its button until the LED blinks fast and accept the macOS pairing
   prompt when it appears.
3. Put the scale where the Mac has a reliable Bluetooth link and run:

   ```bash
   .venv/bin/python scripts/capture-session.py "espresso 18g"
   ```

4. Wait for `connected; recording until Ctrl+C`. Weight notifications start automatically after the handshake. The
   app's tare and timer commands are **not** sent: this is a passive recording. The replay applies those controls when
   the file is used later.
5. Press **Ctrl+C** when done. It also cancels an in-progress scan or connection attempt. The script disconnects,
   flushes the file and prints its path. An unexpected disconnect or failure also leaves a partial file, with a nonzero
   exit status.

One file per connection is written to `spike-captures/<UTC timestamp>-<label>.jsonl`. The file is created exclusively:
a second capture in the same second with the same label fails instead of overwriting the first.

## Format and privacy

The first line is a header. Every following line has `t_ms`, `dir` (`rx`, `tx` or `event`), `hex` and `note`:

```json
{"label":"espresso 18g","started_at":"2026-11-02T12:34:56Z","app":"capture-session.py"}
{"t_ms":1720,"dir":"event","hex":"","note":"scan_start"}
{"t_ms":1826,"dir":"event","hex":"","note":"scan_match"}
{"t_ms":2638,"dir":"rx","hex":"A5 5A 01 01 00 09 00 00 00 00 00 00 00 00 00 00 00","note":"notification"}
```

- `t_ms` is elapsed monotonic time from the start of the session, not a wall-clock timestamp.
- A notification is kept exactly as received. If it contains several frames, it stays on one `rx` line.
- The handshake writes are logged as `tx` with the same bytes the app sends.
- Connection events use fixed safe notes; raw exception messages, devices, names and addresses are never logged.
- Labels and notes are redacted for the Dot's known name/model, the selected device's advertised names and address, and
  MAC- or UUID-shaped text. The file and header are created only after detection, so those identifiers are removed
  before the label is persisted. The raw `hex` bytes are never altered.

## Verify a capture

The replay test can read a capture directly, without copying or editing it. The path must be absolute:

```bash
CAPARAO_CAPTURE="$PWD/spike-captures/20261102-123456-espresso-18g.jsonl" \
  ./gradlew --no-build-cache --rerun-tasks :app:testDebugUnitTest \
  --tests 'io.github.mateuxlucax.caparao.data.scale.RecordedSessionTest'
```

The opt-in test requires at least one decoded notification and fails on frames the decoder does not understand. When
`CAPARAO_CAPTURE` is not set, it is skipped and the existing fixture tests still run. The forced rerun is required
because Gradle does not track `CAPARAO_CAPTURE` as a test task input; without it, a cached result could be reused.

This check is read-only. Review a capture for identifiers before copying it into
`app/src/test/resources/sessions/`; only reviewed fixtures belong in the public repository.

## Read the weights

The file holds the Dot's raw weights: the script sends no tare, so the app's tare is only applied when the file is
replayed, and anything the Dot zeroed at power-on is not in the numbers. This prints one weight frame per line, in
seconds from the session start and in grams:

```bash
.venv/bin/python - spike-captures/20261102-123456-espresso-1.jsonl <<'PY' | less
import json, sys

buffer = b""
for line in open(sys.argv[1], encoding="utf-8"):
    row = json.loads(line)
    if row.get("dir") != "rx":
        continue
    buffer += bytes.fromhex(row["hex"])
    while len(buffer) >= 6:
        if buffer[:2] != b"\xa5\x5a":
            buffer = buffer[1:]  # resync
            continue
        length = int.from_bytes(buffer[4:6], "big")
        end = 6 + length + 2
        if len(buffer) < end:
            break  # the rest arrives with the next notification
        if (buffer[2], buffer[3], length) == (1, 1, 9):
            grams = int.from_bytes(buffer[6:10], "big", signed=True) / 10
            print(f"{row['t_ms'] / 1000:8.2f}  {grams:8.1f}")
        buffer = buffer[end:]
PY
```

`t_ms` is the notification's, so a frame split across two notifications is printed with the second one's time;
acknowledgements, the battery and the `tx` lines are skipped. An espresso is a few hundred lines and a long pour-over
a few thousand; `/` in `less` searches. The [recording guide](acceptance/recording.md) uses this to fill the expected
start, end and yield of each session.

## Limitations

- Passive only: no interactive tare, timer, unit or battery commands. Those interactions can be recorded when the app
  sends them, but this script does not generate them.
- One connection per file. A dropped link ends the capture instead of reconnecting and mixing sessions.
- After a successful connection, no notifications for 3 seconds prints a warning. Ending with no notifications returns
  failure and records `capture_incomplete no_notifications`; nothing was recorded for the brew, so do not discard the
  scale's state thinking the capture is usable.
- macOS is the supported host for now. Linux and Windows are not covered by the documented acceptance run.
- Bleak and macOS handle MTU negotiation and pairing; the script logs the application-level handshake, not every
  platform GATT callback available to the Android transport.
