# Caparaó

This file records project rules that should survive code and workflow changes. Use the [README](README.md) for current
project, development and release instructions; use code and KDoc for implementation details; use GitHub Issues for
decisions and work tracking.

## Public repository and privacy

- Everything committed is public. Never commit credentials, real server addresses or tokens, scale addresses, device
  names or serials. Public examples use `coffee.example.com` and `dev-token`; the real server details belong only in
  the user's private homeserver copy.
- Never log or expose the scale address or server token. Do not send the token anywhere except the configured server;
  never follow redirects on authenticated requests.

## Tracking and pull requests

- GitHub Issues are the only tracker and decision log. Record product decisions in issues; do not create repository
  ADRs.
- Every PR links the issue it closes and uses `.github/pull_request_template.md`. The Assign workflow assigns
  `MateuxLucax` to new and reopened issues and PRs.
- Open PRs for the user to review. Never merge them.

## App copy

- Keep app strings in English and Brazilian Portuguese together. Write direct, calm, plain copy in sentence case; use
  familiar coffee terms and Android's permission names. Keep labels short, distinguish sending from syncing, and be
  clear about where data is stored when it matters. Follow the README's app-copy guidance for detailed patterns.

## Architecture and data

- Keep dependencies flowing one way: `model ← data ← brew ← sync ← ui`. The phone is the source of truth; the scale
  stays in standard mode and brew detection runs in the app.
- Preserve the upload contract: every required field is sent, unknown fields are rejected, and phone UUIDs make retries
  idempotent. Never retry client errors; retry server errors and timeouts. A contract change starts in the exporter's
  canonical contract, then updates its pin, the app and client fixtures.
- Keep user data and the server token out of cloud backups and device transfers. Room schema changes require a
  migration and migration coverage.

## Frozen from the first Play upload

The compatibility reset before the first external user (#396) is the last chance to move or rename these. #432 decides
the final application id and Kotlin package; the freezes bind from the first Play upload under that id and package:

- The application id and the Kotlin package chosen in #432.
- `MainActivity`'s fully qualified name: launcher shortcuts and the widget point at it.
- The `settings` SharedPreferences file and its keys; `SettingsStore`'s KDoc is the final list.
- WorkManager names: the `upload-<brew id>` unique work names, the `upload` tag, the input key and `UploadWorker`'s
  fully qualified name.
- The watch wire format: `WatchSnapshot`'s version, size and field layout, and `WatchStepKind`'s ordinals.
- The Room schema: version 9 is the baseline; there are no migrations below it. The next schema change adds a
  migration and its test again.

## BLE

- Match `TIMEMORE_Dot` or `TES017`, never a bare "dot". Keep the confirmed connection order: MTU 247, bond, notify,
  then grams and standard mode. The Dot bonds to one phone; handle its HCI 0x3E reconnect case.
- `ScaleSource.readings` must deliver every frame; brew detection depends on repeated readings that a `StateFlow` could
  swallow. Derive flow from weight, not the Dot's saturating raw-flow value. Stability is computed in the app; there is
  no device stability flag. Preserve confirmed overload and power-on weight handling. See `DotProtocol.kt` for wire
  details.

## Validation and devices

- Keep screen-flow Robolectric tests feature-scoped under `app/src/test/.../ui/screens/`; shared fixtures live in `ScreenTestSupport`.
- Only one worker may use the emulator at a time. Never install builds on the user's phone without asking first.
- Preserve real-device calibration and validation needs; scripted scales and replay fixtures do not replace checks with
  the real Dot.

## Release signing

- Every release uses the existing release key. Replacing it forces users to uninstall before installing an update.
  Keep signing secrets confined to the signing job; never commit or expose the key or its password.
