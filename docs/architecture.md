# Architecture

## System context

```mermaid
flowchart LR
    dot["Timemore Dot<br/>(BLE, no display)"] -->|"notify FFF1<br/>weight · flow · timer"| app["Caparaó<br/>(this repo)"]
    app -->|"write FFF2<br/>tare · timer · battery query"| dot
    app --> room[("Room<br/>source of truth")]
    room -->|"POST /api/v1/brews<br/>optional"| srv["stub/ (stages 5–7)<br/>caparao-exporter (stage 8)"]
    srv --> db[("SQLite")]
    db --> push["sync every 15 min"] -->|remote write| prom[("Prometheus")] --> graf["Grafana"]
```

- The phone is the source of truth ([ADR 0003](adr/0003-offline-first.md)). Without a server, nothing changes except
  that brews show as "local only".
- The scale stays in its standard mode and only streams readings; brew detection (first drop, end of flow) runs in the
  app ([ADR 0006](adr/0006-brew-detection-in-app.md)).
- The app talks to the scale only while it is open; the screen stays on during a brew ([ADR 0008](adr/0008-connect-while-open.md)).

## Code structure

One Gradle module; packages by layer, features inside `ui/` ([ADR 0011](adr/0011-code-structure.md)). Paths are
relative to `app/src/main/java/io/github/mateuxlucax/caparao/`. Packages marked *planned* arrive with the stage shown.

| Package | Responsibility |
|---------|----------------|
| (root) | `MainActivity` (permission and Bluetooth intents, debug replay extras), `CaparaoApplication`, `AppContainer` (manual DI) |
| `model` | Plain entities: `BrewMode`, `ScaleReading`, `ScaleBattery`, `ScaleState`, `ScaleError`; later `Brew`, `BrewSample`, `SyncError` |
| `data` | `scale/`: `DotProtocol` (UUIDs, commands, CRC-16/MODBUS encoders), `FrameDecoder` (chunks to `ScaleMessage`s), `ScaleSource` (the seam the UI sees), `ScaleClient` (connection, handshake, battery poll, backoff) over `BleTransport` (`AndroidBleTransport`: one GATT operation at a time, bond), `ReplayScaleSource` (recorded sessions for the emulator). `local/`: `SettingsStore`, `BrewDatabase` (Room: `brews`, `brew_samples`), `BrewRepository`. Later `exporter/` (`ExporterClient`, `ServerUrl`) |
| `brew` | Pure brew logic, fed with timed readings: `BrewDetector` with `EspressoDetector` (cup → auto tare → first drop → flow stops) and `FilterDetector` (tare → first water → Done or dripper lifted), `FlowSmoother` (least-squares slope over 1 s), `BrewSummary` (numbers and the ≤ 10 Hz curve) |
| `sync` | *Stage 5*: `UploadWorker`, `UploadScheduler`, `SyncErrors` |
| `ui` | `App` (onboarding gate, tabs, brew and brew detail on top, scale started and stopped with the app's visibility), `theme`, `format`, `components` (logo, pour animation, scale readout ring), `onboarding`, `home`, `brew` (dose → live → saved), `brews` (list, detail, Canvas chart), `settings` |

Outside the app module: `probe/` (stage 0 throwaway recorder, [spike.md](spike.md)), `stub/` (Go development server)
and `contract/` (the upload schema and the JSON files both sides test against).

Imports go one way: `model <- data <- brew <- sync <- ui <- root`. Only `ui` (and the root) uses `R` and
`BuildConfig`. `LayerDependencyTest` enforces both.

## Trust boundaries

- BLE: the Dot accepts one bonded phone at a time; frames from it are parsed defensively (bounded lengths, resync).
- Network: only the server the user configures, with a Bearer token. Cleartext HTTP is allowed for LAN servers.
- Storage: app data is excluded from cloud backups and device transfers (`data_extraction_rules.xml`).
- Repository: no secrets; signing keys come from environment variables; gitleaks runs before every commit.
