# 3. Offline-first: Room is the source of truth, the server is optional

Status: Accepted

## Context

The app must be useful on its own. Exporting to a server, and from there to Prometheus, is one feature among others and
is built last.

## Decision

Every brew is saved to Room on the phone first. A server is optional: onboarding lets the user skip it, and upload jobs
are enqueued only when one is configured. Brews show "local only" or the time they were synced.

## Consequences

- Everything except export works in airplane mode, and nothing waits for the network.
- Data lives on the phone until a server exists; it is excluded from cloud backups, so losing the phone without a
  server loses the history.
- The upload is a separate concern that can change (stub, exporter) without touching recording.
