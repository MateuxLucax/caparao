# Caparaó

Android app for the Timemore Dot scale (Kotlin, Compose). Start from `docs/roadmap.md` for what is done and pending,
`docs/architecture.md` for the layers, `docs/hardware.md` for the BLE protocol, `docs/copy.md` before writing app strings
and the [issues](https://github.com/MateuxLucax/caparao/issues) before changing a decision.
Modules: `app` (phone), `wear` (watch), `wearlink` (shared watch snapshot), `stub` (Go dev server), `contract` (upload
schema). The user writes in Portuguese; code, commits and docs are in English, app strings in EN and pt-BR.

## Tracking

GitHub Issues are the only tracker and the only decision log. Labels: `decision`, `data`, `feature`, `tooling`,
`validation`, `infra`, `documentation`, plus `agent-ready` (a worker can pick it up once its dependencies are merged)
and `needs-user` (depends on the user or a real device). One milestone per release (`0.3.0` and up): the milestone's
last PR sets `VERSION` (`0.3`), so automatic releases come out as `0.3.N`. A new decision is an issue labelled
`decision`, closed when implemented or superseded, and every PR links the issue it closes. There are no ADRs in the
repository: decisions 0001–0026 were archived as closed issues #85–#110.

Create every issue and PR with `MateuxLucax` as assignee until the auto-assign workflow (#142) is merged.

## Agents

Workers run on the Mac through Orca, one worktree per issue (`orca worktree create --issue <n>`).

A new worktree lacks git-ignored files: copy `local.properties` from the main checkout before building (it holds the SDK
path), and read raw captures from the main checkout's `spike-captures/`.

- Pick only `agent-ready` issues whose dependencies are merged. Stop and ask instead of reopening a decision.
- Open a PR and never merge: the user merges, because every green push to `main` is a release.
- `scripts/check.sh` and JVM tests can run in parallel. **The emulator is one worker at a time**: worktrees install the
  same `.debug` package, so a second worker breaks the first one's test.
- Attach with `orca emulator attach` (AVDs `Pixel_10_Pro_XL`, `Wear_OS_XL_Round`) and always set `ANDROID_SERIAL`, so
  nothing reaches the phone. Set `E2E_PORT` for `scripts/e2e.sh` when another server is running.

## Public repository

Everything committed is public. Recordings, logs and docs carry the phone model at most: scale MAC addresses, device
names and serials stay out (`ScaleClient` never logs the address). Servers and tokens in code, tests and docs are
`coffee.example.com` and `dev-token`; the real domain and token live only in the user's homeserver `private/` copy.
`scripts/check.sh` (tests, lint, build, gitleaks) runs locally before every push and mirrors CI.

## UI design

- Every UI task, including a new screen and any change to an existing screen or widget, starts from an editable
  Excalidraw draft, created or updated before implementation. Commit the source under `docs/wireframes/`, link it from
  the originating issue, and attach the exported illustration with a short scenario description to that issue (a
  readable table with one row per scenario when there are several). Use the Orca Excalidraw editor when available, keep
  the draft and issue export current with user feedback, and link the PR to the issue's design reference.
- Motion and transitions follow Material You / Material 3 Expressive patterns, consistent with the app's theme and
  dynamic colors and using the existing Compose Material primitives. Respect the system motion preferences and start
  from the [official motion guidance](https://m3.material.io/styles/motion/overview).
- A draft communicates flow and guides planning; it is not approval. Ask the user about unresolved product choices.
- Finished UI evidence comes from `scripts/screenshots.sh` and follows the table rule under Devices, not the sketch.

## Devices

- Screenshots (docs/screenshots and PR descriptions) come from `scripts/screenshots.sh`, which renders them on the JVM
  with Roborazzi: no emulator, it runs in the cloud too. Cloud sessions build and test with the Android SDK setup script;
  the Mac is only for the emulator and real devices.
- PR screenshots go in a table: one row per scenario with a short description and locale, Light and Dark image columns
  at a constrained width so each pair reads comfortably, and alt text naming state, locale and theme.
- The emulator is for instrumented tests, `scripts/e2e.sh` and on-device checks; with the phone also plugged in, set
  `ANDROID_SERIAL`. adb is at `~/Library/Android/sdk/platform-tools/adb`.
- The user's phone (Galaxy S23): ask before installing anything on it.
- Debug builds are `io.github.mateuxlucax.caparao.debug` ("Caparaó dev") and install next to releases.

## Release

The release key is `~/.config/caparao/release.jks` with its password in the Keychain item `caparao-signing`, both
backed up by the user. Every release must use this key: a new one forces every user to uninstall. Releases come from
the Release workflow (`gh workflow run release.yml`); `scripts/release-apk.sh --install` builds a signed one locally.

## Scale data

`ScaleSource.readings` is a `SharedFlow` delivering every frame: the espresso ends on a run of identical readings, which
a `StateFlow` would swallow. Detector changes are checked against the replay sessions in
`app/src/test/resources/sessions/`; raw captures that are not fixtures stay in the
git-ignored `spike-captures/`.

## Related repositories

- `~/Development/timemore-dot-prometheus-exporter`: the server (Go, SQLite, Prometheus remote write), same contract as
  `contract/` and `stub/`. Images go to GHCR from its CI.
- `~/Development/homeserver`: deploys it as the `caparao` stack (`cafe.<domain>`). The user runs the Ansible playbook.
