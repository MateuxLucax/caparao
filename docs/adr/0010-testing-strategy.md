# 10. Testing strategy: JVM tests, recorded sessions and a local end-to-end test

Status: Accepted

## Context

The logic that matters (frame parsing, brew detection, upload rules) must be right, but it depends on a BLE scale that
only exists on a device. Instrumented tests are slow and, while the repository is private, CI minutes are limited
([ADR 0015](0015-acceptance-and-lean-ci.md)).

## Decision

- **JVM unit tests** (`app/src/test`) for everything pure: codec and decoder, detectors, chart scaling, formatting,
  ViewModels with fakes, `ExporterClient` against a local HTTP server, package rules, translations.
- **Recorded sessions**: the stage 0 spike records real Dot sessions (frames with timestamps). They live in
  `app/src/test/resources/sessions/` and drive the decoder and detector tests, and a `ReplayScaleSource` that plays
  them on the emulator.
- **Contract tests**: shared JSON files in `contract/` checked by the app and by the server side.
- **Instrumented tests** (`app/src/androidTest`): Compose UI, Room DAO and migrations, run on a local emulator.
- **End-to-end** (`scripts/e2e.sh`, stage 6): replayed brew → app → Room → stub.
- **Device scripts** in `docs/acceptance/` for what needs the real scale, with each run recorded.

## Consequences

- Fast feedback without a device, real data in the tests.
- Recordings must be scrubbed of device identifiers before they are committed.
