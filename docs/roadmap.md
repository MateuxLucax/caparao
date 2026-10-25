# Roadmap

Each stage ends with something that runs, and closes only when its acceptance criteria are met
([ADR 0015](adr/0015-acceptance-and-lean-ci.md)). Automated criteria run in `scripts/check.sh` or on the local
emulator; device criteria are scripts in [acceptance/](acceptance/README.md).

| # | Stage | Status |
|---|-------|--------|
| 0 | Hardware spike | Planned |
| 1 | Foundation | Done, pending first CI run |
| 2 | Scale | Planned |
| 3 | Brews | Planned |
| 4 | Brew history | Planned |
| 5 | Contract, stub and sync | Planned |
| 6 | Release and end-to-end test | Planned |
| 7 | Screens waiting for wireframes | Waiting for designs |
| 8 | Prometheus exporter | Planned, last |

## 0. Hardware spike

Confirm the protocol on the real Dot and record sessions for the tests.

- Throwaway `BluetoothGatt` probe: scan, bond, notify, tare, timer, battery; logs every frame (hex + timestamp) to JSONL.
- Recordings: tare, 2 espressos, 2 filters, cup lifted mid-brew, physical button, 5 minutes idle while connected.
- Answer in [hardware.md](hardware.md): is a keep-alive needed? Does the button send a notification? Can the scale's
  flow go negative? Does it power off while connected?

Acceptance:
- `hardware.md` answers the four questions, each citing the recording that proves it.
- At least 7 sessions in `app/src/test/resources/sessions/`, each with a short note of what happened.
- No MAC address or device name in the recordings (the capture script replaces them).

## 1. Foundation

- Gradle project (compileSdk 37, minSdk 31), packages `model <- data <- brew <- sync <- ui <- root`, `LayerDependencyTest`.
- Material 3 dynamic color, English and pt-BR strings with `TranslationsTest`, Home / Brews / Settings tabs, launcher icon.
- `docs/`, ADRs 0001–0015, `scripts/check.sh`, gitleaks pre-commit hook, lean CI.

Acceptance:
- `scripts/check.sh` passes locally in under 3 minutes.
- `LayerDependencyTest` fails on a layer violation (covered by a test with a deliberate violation).
- `TranslationsTest` fails when a string exists only in English.
- The APK opens with the three tabs in light and dark theme (`AppTest` on the emulator); CI is green in under 10 minutes.

## 2. Scale

- `DotProtocol` (pure codec, CRC-16/MODBUS out), `FrameDecoder` (buffering, resync on `A5 5A`, incoming CRC ignored).
- `ScaleClient` on `BluetoothGatt`: scan by `TIMEMORE_Dot`/`TES017`, 3 connect attempts, MTU 247, bond, notify,
  init (grams, standard mode), operation queue, battery every 60 s, reconnect with 1–10 s backoff, remembered address.
- `ScaleSource` seam with a `ReplayScaleSource` for tests, the emulator and screenshots.
- Onboarding: welcome (animation), Bluetooth permissions, connect the scale (live data or an explained error with a
  copyable debug log). Home: status, battery, weight, flow, timer, tare and timer controls.

Acceptance:
- Codec: tare and timer frames byte-for-byte equal to the references; decoder handles several frames per notification,
  a frame split across two, garbage before `A5 5A` and negative weight.
- `ScaleClient` against a fake GATT: connect → MTU → bond → notify → init order; reconnects after a drop with backoff.
- Onboarding and Home ViewModels, fed by `ReplayScaleSource`, show connected, no battery yet and each error.
- Device script ([acceptance/scale.md](acceptance/README.md)): pair from scratch in < 30 s, weight within ±0.1 g of
  the scale, tare responds in < 1 s, turning the Dot off and on reconnects by itself, a Dot paired elsewhere shows the
  reset hint.

## 3. Brews

- Espresso, fully automatic: cup placed → stable → auto tare → timer starts at the first drop → stops when weight rises
  ≤ 0.2 g in 4 s → saved. Optional stop button, discard.
- Filter, free pour: tare, starts at the first water, live weight / flow / time / ratio; ends on "done" or when the
  dripper is lifted (drop ≥ 30 g or 12 %, held 5 s).
- Dose: weigh it on the scale ("use weight") or type it; last dose remembered per mode.
- Pure detectors in `brew/`; Room (`brews`, `brew_samples`); brew screen keeps the screen on.

Acceptance:
- Every espresso recording has its expected result noted (start ±0.5 s, end ±1 s, yield ±0.3 g); the detector meets all.
- Synthetic cases: cup lifted before the first drop (nothing saved), slow flow pausing 3 s (does not end), manual stop,
  scale disconnecting mid-brew (saved and marked interrupted).
- Room DAO on an in-memory database: save, list by mode, delete cascades to samples; migration test.
- Device script: one real espresso and one real filter are saved with the numbers seen; the screen stays on.

## 4. Brew history

- List grouped into filter and espresso: mode icon, dose → yield, 1:X, time, date, "synced HH:MM" or "local only".
- Brew detail: weight and flow curves drawn on a Compose Canvas, summary, delete.

Acceptance:
- ViewModel tests: grouping, newest first, "local only" without a server, sync time with one.
- Chart scaling is a pure, tested function (0–40 g and 0–500 g axes, empty curve).
- In airplane mode, 50 brews scroll smoothly and a detail opens in < 300 ms.

## 5. Contract, stub and sync

- [data.md](data.md): `POST /api/v1/brews`, Bearer token, upsert by the phone-generated `id` ([ADR 0007](adr/0007-idempotent-upload.md)).
- `stub/`: Go standard library only; validates, upserts in memory, logs, `GET /api/v1/brews`, `GET /healthz`, Dockerfile.
- `ExporterClient` (`HttpURLConnection` + `org.json`), one `UploadWorker` per brew (network constraint, backoff).
- Optional server step in onboarding (with Skip) and in Settings, with a connection test; local network permission only
  for a local server.

Acceptance:
- Contract tests: the same JSON files in `contract/` are checked by the app and by the stub (`go test`).
- `ExporterClient` against a local HTTP server: 2xx marks synced; 5xx and timeouts retry; 401 and other 4xx show an
  error without retrying; a redirect is a clear error.
- No upload job is enqueued without a server.
- Device script: a brew recorded in airplane mode uploads by itself when the network returns, and the stub holds one
  record even after a resend.

## 6. Release and end-to-end test

- Signed APK on GitHub Releases. Manual release (`workflow_dispatch` or tag) while the repository is private; automatic
  on every green push once it is public.
- `EndToEndTest` on the local emulator (`scripts/e2e.sh`): a recorded espresso is replayed, saved, uploaded and checked
  in the stub. Moves to CI when the repository is public.
- EN and pt-BR screenshots, README troubleshooting.
- Before going public: gitleaks over the whole history; no server address or token in fixtures or screenshots.

Acceptance:
- `scripts/e2e.sh` passes locally.
- Installing from Releases works, and the next version installs over it without losing data.
- `gitleaks git` is clean over the whole history.

## 7. Screens waiting for wireframes

Refined filter and espresso modes (recipes, bloom, pour targets, pace hints), detailed charts and trends, more brew
metadata. Acceptance criteria are written together with the wireframes.

## 8. Prometheus exporter (last)

Separate repository `caparao-exporter` (Go):

- **SQLite is the source of truth.** The API writes the brew and its curve there first (upsert by `id`) and answers 2xx
  only after the commit, so Prometheus never blocks the app.
- **A separate sync process** runs every 15 minutes (configurable): reads what has not been sent, remote-writes it with
  the original timestamps, then advances its checkpoint. If Prometheus is down, the next run retries.
- Per-brew summary (`caparao_brew_yield_grams`, `caparao_brew_dose_grams`, `caparao_brew_ratio`,
  `caparao_brew_duration_seconds`, `caparao_brew_time_to_first_drop_seconds`,
  `caparao_brew_flow_avg_grams_per_second`, label `mode`) and the curve (`caparao_brew_weight_grams`,
  `caparao_brew_flow_grams_per_second`) with each sample's timestamp. A command re-sends everything.
- Docker Compose with Prometheus and Grafana and a dashboard; the app's end-to-end test switches from the stub to it.

Acceptance:
- The `contract/` files pass against the exporter exactly as against the stub.
- Upsert on a real SQLite: re-sending an `id` duplicates neither the brew nor its samples.
- With Prometheus down the API keeps accepting brews; when it is back, the next sync sends everything, nothing lost or duplicated.
- Integration test with a real Prometheus: summaries at the brew's end time, curve at each sample's time.
- `scripts/e2e.sh` passes against the exporter, and the brew shows in Grafana at the right time.
