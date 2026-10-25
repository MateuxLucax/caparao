# 16. One WorkManager job per brew, enqueued only with a server

Status: Accepted

## Context

Brews are saved on the phone first ([ADR 0003](0003-offline-first.md)) and copied to the user's server when one is
set. Uploads are idempotent ([ADR 0007](0007-idempotent-upload.md)), so retrying is always safe. A brew can be saved
in airplane mode, the server can be down, and the token can be wrong; the app is not open when the network returns.

## Decision

- WorkManager (`androidx.work:work-runtime`, allowed by [ADR 0004](0004-minimal-dependencies.md)) runs one unique
  `OneTimeWorkRequest` per brew, named `upload-<brew id>` and tagged `upload`, with a `CONNECTED` network constraint
  and exponential backoff from 30 s.
- `UploadScheduler` enqueues after the brew is in Room (the job always finds it), only when a URL and token are saved:
  `KEEP` after a save, `REPLACE` for every unsynced brew when a server is saved or changed, or on Retry in the Brews
  tab (this also restarts jobs that failed or wait in backoff). Removing the server cancels the tag; discarding a brew
  cancels its job.
- `UploadRunner` does the work, so the worker (`UploadWorker`, root package, name persisted) stays a few lines:
  `2xx` marks the brew synced and records the time; `5xx`, timeouts and unreachable servers retry; `401`, other
  `4xx`, redirects and a blocked local network fail without retry (contract/README.md). The last error is kept in
  `SettingsStore` for the Brews tab. A later success clears it, except a rejected body of another brew, which stays
  until that brew goes through or the server is saved again.
- No periodic job: a failed (not retryable) brew waits for the user to fix the problem, which triggers a resend.

## Consequences

- Brews upload by themselves when the network returns, with no work enqueued while no server is set.
- Many unsynced brews make many small jobs; WorkManager runs them in parallel, which the upsert makes harmless.
- The runner is plain Kotlin and tested on the JVM with a real local HTTP server and fakes; WorkManager itself is not
  unit tested (the device script in [acceptance/sync.md](../acceptance/sync.md) covers it).
