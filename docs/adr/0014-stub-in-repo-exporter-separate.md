# 14. Stub server in this repository, exporter in its own

Status: Accepted

## Context

The upload path (client, worker, settings, connection test) is built in stage 5, but the Prometheus exporter is the last
stage. The app needs a real HTTP server to develop and test against before then.

## Decision

`stub/` in this repository: a minimal Go server (standard library only) implementing the contract
([data.md](../data.md)): validation, in-memory upsert by `id`, logging, `GET /api/v1/brews` and `GET /healthz`. It is
for development and the end-to-end test only. The real server, `timemore-dot-prometheus-exporter`, is a separate repository built in
stage 8 (SQLite as source of truth, a separate sync to Prometheus every 15 minutes), mirroring health-connect-bridge and
its exporter.

## Consequences

- The contract is exercised by real HTTP from stage 5.
- Two server implementations of one contract: shared JSON files in `contract/` keep them aligned.
