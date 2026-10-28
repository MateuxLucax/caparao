# 20. Export brews as CSV and JSON through the share sheet

Status: Proposed

## Context

The goal asks to export data (CSV, JSON) besides the Prometheus integration. Offline-first
([ADR 0003](0003-offline-first.md)) means the phone holds the only full copy for users without a server, and there is
no way to get it out today.

## Decision

- A `BrewExporter` in `data/` writes from Room, no new dependency ([ADR 0004](0004-minimal-dependencies.md)):
  - JSON: an array of brews in the same shape as the upload body in `contract/`, curve included, so the file can be
    re-posted to a server and is checked by the same contract tests.
  - CSV: `brews.csv` with one row per brew (the summary fields, recipe name, notes), and optionally `samples.csv`
    (`brew_id, at_ms, weight_g, flow_g_s`). Both zipped into one file when samples are included. Dot as decimal
    separator and ISO 8601 times, whatever the locale, so spreadsheets and scripts read them the same way.
- Entry points: "Export" in Settings (all brews, format choice) and "Share" in the brew detail (one brew). The file is
  written to the cache directory and shared with `ACTION_SEND` through a `FileProvider`; no storage permission.
- Import is out of scope; the JSON shape keeps the door open.

## Consequences

- The JSON export reuses the contract, so a schema change shows up in one place.
- `FileProvider` adds a manifest entry and an XML paths file, nothing else.
- Large histories write on a background dispatcher; 1 000 brews with samples is a few MB, fine for the share sheet.
