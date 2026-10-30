# Caparaó

Android app for the Timemore Dot scale (Kotlin, Compose). Start from `docs/roadmap.md` for what is done and pending,
`docs/architecture.md` for the layers, `docs/hardware.md` for the BLE protocol, `docs/copy.md` before writing app strings and `docs/adr/` before changing a decision.
The user writes in Portuguese; code, commits and docs are in English, app strings in EN and pt-BR.

## Public repository

Everything committed is public. Recordings, logs and docs carry the phone model at most: scale MAC addresses, device
names and serials stay out (`ScaleClient` never logs the address). Servers and tokens in code, tests and docs are
`coffee.example.com` and `dev-token`; the real domain and token live only in the user's homeserver `private/` copy.
`scripts/check.sh` (tests, lint, build, gitleaks) runs locally before every push and mirrors CI.

## Devices

- Screenshots (docs/screenshots and PR descriptions) come from `scripts/screenshots.sh`, which renders them on the JVM
  with Roborazzi: no emulator, it runs in the cloud too.
- Emulator `emulator-5554` for instrumented tests and `scripts/e2e.sh` only; with the phone also plugged in,
  set `ANDROID_SERIAL`. adb is at `~/Library/Android/sdk/platform-tools/adb`.
- The user's phone (Galaxy S23): ask before installing anything on it.
- Debug builds are `io.github.mateuxlucax.caparao.debug` ("Caparaó dev") and install next to releases.

## Release

The release key is `~/.config/caparao/release.jks` with its password in the Keychain item `caparao-signing`, both
backed up by the user. Every release must use this key: a new one forces every user to uninstall. Releases come from
the Release workflow (`gh workflow run release.yml`); `scripts/release-apk.sh --install` builds a signed one locally.

## Scale data

`ScaleSource.readings` is a `SharedFlow` delivering every frame: the espresso ends on a run of identical readings, which
a `StateFlow` would swallow. Detector changes are checked against the replay sessions in
`app/src/test/resources/sessions/`; real recordings come from the probe via `scripts/pull-sessions.sh` into the
git-ignored `spike-captures/`.

## Related repositories

- `~/Development/timemore-dot-prometheus-exporter`: the server (Go, SQLite, Prometheus remote write), same contract as
  `contract/` and `stub/`. Images go to GHCR from its CI.
- `~/Development/homeserver`: deploys it as the `caparao` stack (`cafe.<domain>`). The user runs the Ansible playbook.
