# 18. Recipes stored in Room, one model for espresso and filter

Status: Accepted

## Context

The project goal asks for recipe profiles that can be created, edited and reused for espresso and filter (V60, Kalita,
AeroPress and others), with dose, grind, temperature, target ratio and steps. Today the app only remembers the last dose
per mode (`SettingsStore`) and every brew starts from scratch. Stage 7 of the roadmap waits for wireframes; this record
fixes the data model so the screens can be drawn against it.

## Decision

- A `Recipe` in `model/`: `id` (UUID, generated on the phone), `name`, `mode` (`BrewMode`), `method` (free text with
  suggestions: V60, Kalita, AeroPress, Espresso…), `doseGrams`, `targetRatio` (the X of 1:X), optional `grind` (free
  text, grinders have no common scale), optional `waterTempCelsius`, optional `notes`, `createdAt`, `updatedAt`.
- Steps are an ordered list of `RecipeStep`: `kind` (`BLOOM`, `POUR`, `WAIT`, `SWIRL`), `targetWeightGrams` (cumulative
  on the scale, null for `WAIT`/`SWIRL`) and `targetAtMs` (when it should start, from the first water). Cumulative
  weight matches what the scale shows, so a step needs no arithmetic while pouring.
- Espresso recipes have no steps: dose, target yield (from the ratio) and a target time window (`targetMinMs`,
  `targetMaxMs`). The automatic detector ([ADR 0006](0006-brew-detection-in-app.md)) stays as it is.
- Room tables `recipes` and `recipe_steps` (cascade delete), migration 1 → 2 with a migration test. `brews` gains a
  nullable `recipe_id` (no foreign key: deleting a recipe keeps the brews) and a copy of the recipe name, so history
  still reads well after a recipe is renamed or deleted.
- The brew screen starts from a recipe or from "free brew"; the last used recipe per mode is preselected, replacing the
  remembered dose. Two built-in recipes are inserted on first run as ordinary, editable rows, taken from the first
  brews recorded on the exporter (2026-10, 2 espresso and 2 filter):
  - Espresso: 18 g, 1:2.4 (43 g), 28–34 s.
  - V60: 18 g, 1:16.7; bloom to 50 g at 0:00, pours to 100 g at 0:45, 150 g at 1:25 and 300 g at 2:00, then drawdown
    (about 3:30 total).
- Recipes stay on the phone. The upload carries only `recipe_name` (see [ADR 0021](0021-brew-notes.md)).

## Consequences

- One table pair covers both modes; espresso simply has no steps.
- The free-text grind keeps the model simple at the cost of no grind analytics across grinders.
- The schema migration is the app's first; it sets the pattern (exported schemas, `MigrationTestHelper`).
- The guided brew ([ADR 0019](0019-guided-brew.md)) reads `RecipeStep`s and never writes them.
