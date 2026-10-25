# 15. Acceptance criteria per stage, local test runs and lean CI

Status: Accepted

## Context

The app should have a solid test layer from the first stage. The repository is private on GitHub Free, so Actions
minutes are limited, while the developer's Mac can run builds, tests and an emulator.

## Decision

- Every stage in [roadmap.md](../roadmap.md) lists acceptance criteria and closes only when they are met. Criteria that
  need the real scale are scripts in [acceptance/](../acceptance/README.md) with each run recorded.
- `scripts/check.sh` is the local gate: unit tests, lint, debug build, instrumented-test compile, gitleaks.
- CI is one job on pushes to `main` (not on every branch push): the same tasks as `check.sh`, Gradle cache,
  `concurrency` cancelling superseded runs, docs-only changes ignored. Instrumented and end-to-end tests run on the local
  emulator.
- Screenshots of every screen (English and Portuguese, light and dark) are refreshed with `scripts/screenshots.sh`
  whenever a screen changes, and shown in the README and [docs/screenshots](../screenshots/README.md).
- Once the repository is public (free minutes), the emulator job and automatic releases move to CI.

## Consequences

- A few minutes of Actions per push to `main`.
- Instrumented results depend on running them locally before merging; the acceptance records show when they last ran.
