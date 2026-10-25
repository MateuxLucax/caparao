# 11. Code structure: layered packages, manual DI and ViewModels

Status: Accepted

## Context

Same size and shape as health-connect-bridge (one module, a few screens, one maintainer), whose structure worked well
(its ADR 0013).

## Decision

One Gradle module with packages by layer and features inside `ui/`: `model` (plain entities) ← `data` (scale, local
storage, exporter client) ← `brew` (pure brew logic) ← `sync` (upload jobs) ← `ui` ← root (activity, application,
`AppContainer`). Only `ui` and the root use `R` and `BuildConfig`. `LayerDependencyTest` checks imports and fully
qualified names.

Manual dependency injection through one `AppContainer` owned by the `Application`, created when the first dependency
appears (stage 2). One ViewModel per screen exposing an immutable UI state as a `StateFlow`; ViewModels get no
`Context`. No domain layer or use cases, Hilt, Koin or Navigation Compose.

## Consequences

- Screens get data only through their ViewModel; logic is testable without Android.
- Names Android stores (activity names, WorkManager worker classes, preference keys) must never change once released.
