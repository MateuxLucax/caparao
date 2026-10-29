# 21. Brew metadata: coffee, grind, temperature, notes and rating

Status: Accepted

## Context

To compare brews and learn from them, a brew needs more than the scale's numbers: which coffee, the grind setting, the
water temperature and how it tasted. The roadmap's stage 7 lists "more brew metadata". Today a `Brew` holds only what
the scale and the detector produce.

## Decision

- New nullable columns on `brews`: `recipe_id` and `recipe_name` ([ADR 0018](0018-recipes.md)), `coffee` (free text),
  `grind` and `water_temp_celsius` (copied from the recipe, editable), `rating` (1–5) and `notes`. Same migration as
  the recipes.
- Editing happens after the brew: the save screen shows them prefilled from the recipe, and the brew detail can edit
  them later. Nothing is asked before the brew, so the main flow (choose recipe, tare, brew) stays a few taps.
- The last coffee used is suggested on the next brew, since a bag lasts many brews.
- The upload contract gains the same optional fields; an edit after the sync resends the brew, which the idempotent
  upsert already handles ([ADR 0007](0007-idempotent-upload.md)). The exporter stores them in SQLite and exposes
  `coffee` and `recipe_name` as labels only on the summary metrics (low cardinality); notes and rating stay out of
  Prometheus. The stub and the exporter change together with `contract/`.

## Consequences

- An edit marks the brew unsynced again; `syncedAt` is cleared and one upload job is enqueued.
- Free-text coffee names may vary ("Caparaó natural", "caparao nat."); a picker of past values reduces it without a
  coffees table. A real coffees table can come later if trends per coffee need it.
- Adding labels to the exporter's metrics is a breaking change for existing series; the dashboard is updated with it.
