# Architecture Decision Records

Short records of the decisions behind the app (Status, Context, Decision, Consequences).

| # | Decision | Status |
|---|----------|--------|
| [0001](0001-own-minimal-app.md) | Our own minimal app instead of Timemore's | Accepted |
| [0002](0002-native-ble.md) | BLE through the platform's BluetoothGatt, without a library | Accepted |
| [0003](0003-offline-first.md) | Offline-first: Room is the source of truth, the server is optional | Accepted |
| [0004](0004-minimal-dependencies.md) | Minimal dependencies | Accepted (Room amendment planned) |
| [0005](0005-compose-material3-dynamic-color.md) | Jetpack Compose and Material 3 with dynamic color | Accepted |
| [0006](0006-brew-detection-in-app.md) | Brew detection in the app, with the scale in standard mode | Accepted |
| [0007](0007-idempotent-upload.md) | Idempotent upload: id generated on the phone, upsert on the server | Accepted |
| [0008](0008-connect-while-open.md) | Connect to the scale only while the app is open | Accepted |
| [0009](0009-min-sdk-31.md) | minSdk 31 (Android 12) | Accepted |
| [0010](0010-testing-strategy.md) | Testing strategy: JVM tests, recorded sessions and a local end-to-end test | Accepted |
| [0011](0011-code-structure.md) | Code structure: layered packages, manual DI and ViewModels | Accepted |
| [0012](0012-sideloaded-apk-releases.md) | Sideloaded APK on GitHub Releases | Accepted |
| [0013](0013-canvas-charts.md) | Charts drawn on a Compose Canvas | Accepted |
| [0014](0014-stub-in-repo-exporter-separate.md) | Stub server in this repository, exporter in its own | Accepted |
| [0015](0015-acceptance-and-lean-ci.md) | Acceptance criteria per stage, local test runs and lean CI | Accepted |
| [0016](0016-upload-jobs.md) | One WorkManager job per brew, enqueued only with a server | Accepted |
| [0017](0017-local-network-permission.md) | Ask for the local network permission when the server is local | Accepted |
| [0018](0018-recipes.md) | Recipes stored in Room, one model for espresso and filter | Proposed |
| [0019](0019-guided-brew.md) | Guided brew: a pure step tracker driven by the readings | Proposed |
| [0020](0020-data-export.md) | Export brews as CSV and JSON through the share sheet | Proposed |
| [0021](0021-brew-notes.md) | Brew metadata: coffee, grind, temperature, notes and rating | Proposed |
| [0022](0022-compare-and-trends.md) | Compare brews and show trends on the Canvas charts | Proposed |
| [0023](0023-watch-companion.md) | Watch companion: a Wear OS mirror fed by the phone | Proposed |
