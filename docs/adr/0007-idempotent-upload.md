# 7. Idempotent upload: id generated on the phone, upsert on the server

Status: Accepted

## Context

Uploads are retried until the server answers 2xx. A request can reach the server and still fail on the way back
(timeout, network change), so the same brew can arrive twice. The server stores brews in a database.

## Decision

Each brew gets a random UUID when it is created on the phone. `POST /api/v1/brews` carries it as `id`, and the server
upserts on it (`201` new, `200` existing). The phone marks a brew synced only after a 2xx.

## Consequences

- Retries are always safe; the stub and the exporter must both honor the upsert (checked by shared contract files).
- Editing a brew later can reuse the same request.
