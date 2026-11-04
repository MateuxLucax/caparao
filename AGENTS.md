# Caparaó

Android app for the Timemore Dot scale (Kotlin, Compose). The README is the project overview; the
[issues](https://github.com/MateuxLucax/caparao/issues) are the only tracker and decision log.
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

## App copy

Every string lives in `values/strings.xml` (English, default) and `values-pt-rBR/strings.xml`
(Portuguese, Brazil). Write both locales together, in the voice a native speaker would use;
`TranslationsTest` keeps the keys and format arguments in sync. A new key goes in both files under the
same comment.

- Voice: direct and useful (one job per line); essential (only text that changes what the user does
  next — no default subtitles, no explanations of obvious controls, no repeated confirmations); calm
  (short sentences, one idea at a time); warm on onboarding, understated elsewhere; plain — familiar
  words, bar coffee terms, Android's own permission names.
- Honest about data: say where brews are stored and whether they are synced when it matters
  (`Only on this phone`); distinguish saved, sent and synced.
- Mechanics: sentence case everywhere including titles and buttons. Use `…` for ongoing states
  (`Connecting…`); full sentences end with a period, titles/labels/buttons do not. Formatting comes
  from code; units are `g` and `g/s`; pt-BR decimal comma comes from the locale. Ratio is `1:%1$s`.
  Address is "you"/imperative in EN, "você"/imperative (`Ligue`, `Segure`) in pt-BR. Natural EN
  contractions. `Caparaó` is never translated. Say "the scale" in instructions, "the Dot" when the
  model matters; pt-BR: "a balança" / "a Dot".
- Vocabulary (EN / pt-BR): brew/preparo, dose, yield/bebida, water/água, ratio/razão, flow/fluxo,
  tare/tara, first drop/primeira gota, send/enviar (upload action) vs sync/sincronizar (status),
  recipe/receita, step/etapa, pour/despejo, wait/espera, grind/moagem, coffee/café, rating/nota.
  French press: steep → Infusão. "Shot" only in espresso instructions. French-press guide order:
  no press instruction before the deadline — remove first, then press and serve.
- Patterns: buttons name the action in one or two words (`Tare`, `Save`, `Retry`); pt-BR uses the
  infinitive (`Salvar`, `Descartar`). Errors are title (problem, no blame, no period) plus hint
  (the action, ending with that action). Empty states are one short line, no subtitle.
  Ongoing states are a present participle + `…`; finished states a short past participle (`Saved`).
  Confirmations put the question in the title (`Delete this brew?`) with `Delete`/`Cancel`.
  Widget: estimates (beverage and caffeine) are prefixed `≈`; dose is recorded and has no `≈`.
  Brewed amounts only, never consumption. Explain why Bluetooth/local-network access is needed
  before asking. Keep device addresses, device names and real server details out of public
  examples; use `coffee.example.com`.

## UI design

- Every UI task, including a new screen and any change to an existing screen or widget, starts from an editable
  Excalidraw draft, created or updated before implementation and kept in the originating GitHub issue together with its
  exported illustration and a short scenario description (a readable table with one row per scenario when there are
  several). Use the Orca Excalidraw editor when available, keep the draft and issue export current with user feedback,
  and have the PR link the issue's design reference. The issue is the only home for drafts and their SVG/PNG exports.
- Motion and transitions follow Material You / Material 3 Expressive patterns, consistent with the app's theme and
  dynamic colors and using the existing Compose Material primitives. Respect the system motion preferences and start
  from the [official motion guidance](https://m3.material.io/styles/motion/overview).
- A draft communicates flow and guides planning; it is not approval. Ask the user about unresolved product choices.

## Devices

- Screen flows are covered by JVM Robolectric tests (`ScreenTests`, `WatchTests`): no emulator, they run in the cloud too.
  Cloud sessions build and test with the Android SDK setup script; the Mac is only for the emulator and real devices.
- PRs do not require a screenshot table. If a change is visual, attach evidence from a device or emulator run when useful.
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

## BLE

Wire format, GATT identifiers, connection order and the frame layout live in
`DotProtocol.kt` (KDoc). Match `TIMEMORE_Dot` / `TES017` only, never a bare "dot". The Dot bonds to one
phone (HCI 0x3E); MTU 247 → bond → notify → grams + standard mode is the connection order. Confirmed
on a real Dot: the raw flow saturates at 99.9 and brew logic does not rely on it (flow is derived from
weight in `FlowSmoother`); the overload flag
is payload byte 8; weight reads negative at power-on once removed; no stability flag exists
(stability is computed in the app). No keep-alive is needed; battery arrives as a read reply or an
unreliable push — poll and accept either.

## Data

- Layers go one way: `model ← data ← brew ← sync ← ui` (`LayerDependencyTest` enforces it; only
  `ui` and the root use `R`/`BuildConfig`). The phone is the source of truth; the scale stays in
  standard mode and brew detection runs in the app. The app talks to the scale only while open; the
  screen stays on during a brew.
- Room sources are in `app/schemas/`; every version change needs a migration and a migration test.
  Tables `brews` and `brew_samples` (curve, ≤10 Hz, deleted with its brew) cover the brews; the
  exported schema keeps compatibility guarantees.
- Upload contract (`contract/`): `POST /api/v1/brews` with a Bearer token; every field required,
  unknown ones rejected; `id` is a phone UUID the server upserts (retries never duplicate); `201`
  new / `200` resent; 4xx never retried, 5xx/timeouts retried. One WorkManager job per brew, only
  while a server is set. A contract change starts in `contract/` first.
- App data is excluded from cloud backups and device transfers (`data_extraction_rules.xml`); the
  token is never logged, put in an error, or sent to another location: redirects are never followed.
  Limits stay in `contract/README.md`.

## Validation

Automated: `scripts/check.sh` (JVM tests incl. `RecordedSessionTest` replay and `ScreenTests`,
lint, debug build, `compileDebugAndroidTestKotlin`, Go contract tests) and `./gradlew
connectedDebugAndroidTest` on the emulator. Manual runs need the real Dot and are recorded against
the issue tracker (date, app version, phone, Android version, pass/fail per step), never as repo
prose:

- Scale: pair from scratch < 30 s, ±0.1 g against a reference, tare < 1 s, reconnect after power
  loss and after returning from background, re-pair reset hint, no address/name in the debug log.
- Brews: real espresso and filter saved with observed numbers (yield ±0.3 g, start ±0.5 s, end
  ±1 s), screen stays on, lift/disconnect handled as documented.
- Sync: airplane-mode brew uploads on reconnect without opening the app; wrong-token and local-
  network-denied errors show their fix; no upload without a server.

## Related repositories

- `~/Development/timemore-dot-prometheus-exporter`: the server (Go, SQLite, Prometheus remote write), same contract as
  `contract/` and `stub/`. Images go to GHCR from its CI.
- `~/Development/homeserver`: deploys it as the `caparao` stack (`cafe.<domain>`). The user runs the Ansible playbook.
