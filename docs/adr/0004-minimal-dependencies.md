# 4. Minimal dependencies

Status: Accepted (amendment for Room and KSP planned in stage 3)

## Context

The app is maintained by one person and handles personal data. Every library adds update work and code that runs with
access to it.

## Decision

Runtime dependencies are limited to `activity-compose`, Compose Material 3 and, as screens get state, the AndroidX
lifecycle Compose artifacts and WorkManager. HTTP uses `HttpURLConnection`, JSON the platform's `org.json`, BLE the
platform API ([ADR 0002](0002-native-ble.md)), charts a Compose Canvas ([ADR 0013](0013-canvas-charts.md)). No DI
framework, no networking or serialization library, no analytics.

**Planned amendment (stage 3):** brews and their curves need queries (by mode, unsynced, by date), so Room with KSP will
be added. The amendment will record the exact artifacts.

## Consequences

- Small APK and dependency surface; same approach as health-connect-bridge.
- More hand-written code for HTTP, JSON and BLE.
