# Recording a Dot session

`scripts/capture-session.py` scans for the Timemore Dot, runs the handshake from [hardware.md](hardware.md) and records
every FFF1 notification until Ctrl+C. The JSONL is compatible with `ReplayScaleSource` and `RecordedSessionTest`, so a
capture can be inspected before it becomes a fixture. The repository is public: raw captures stay in the git-ignored
`spike-captures/` until reviewed.

The script is external to the app, not a recorder inside it. **It owns the BLE link**, so Caparaó and every other app
must be disconnected while it runs. The Dot also remembers one bonded device: connecting from the Mac may require
holding the Dot's button until its LED blinks fast, and after the capture the phone may need to pair again.

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
