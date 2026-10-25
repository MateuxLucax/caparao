# 4. Minimal dependencies

Status: Accepted, amended 2026-10-25 (Room and KSP)

## Context

The app is maintained by one person and handles personal data. Every library adds update work and code that runs with
access to it.

## Decision

Runtime dependencies are limited to `activity-compose`, Compose Material 3 and, as screens get state, the AndroidX
lifecycle Compose artifacts and WorkManager. HTTP uses `HttpURLConnection`, JSON the platform's `org.json`, BLE the
platform API ([ADR 0002](0002-native-ble.md)), charts a Compose Canvas ([ADR 0013](0013-canvas-charts.md)). No DI
framework, no networking or serialization library, no analytics.

**Amendment (2026-10-25, stage 3):** brews and their curves need queries (newest first, unsynced, by id) and atomic
inserts, so the app uses `androidx.room:room-runtime` with `androidx.room:room-compiler` through KSP
(`com.google.devtools.ksp`). Hand-written `SQLiteOpenHelper` code was the alternative; Room's compile-time checked
queries, `Flow` results and exported schemas (the base for migration tests) are worth one library. Nothing else is
added with it.

## Consequences

- Small APK and dependency surface; same approach as health-connect-bridge.
- More hand-written code for HTTP, JSON and BLE.
