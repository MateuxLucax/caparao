# 22. Compare brews and show trends on the Canvas charts

Status: Accepted

## Context

The goal asks to compare brews and show clear charts. The detail screen draws one brew's weight and flow
([ADR 0013](0013-canvas-charts.md)); there is no way to put two brews side by side or to see how a recipe evolves.
Grafana covers long-term trends for users with the exporter, but the app must work without a server
([ADR 0003](0003-offline-first.md)).

## Decision

- Compare: from the history, long-press to select two to four brews of the same mode, then "Compare" overlays their
  weight curves on one Canvas chart, aligned at the start, one colour per brew, with a summary table below (dose,
  yield, ratio, time, flow, rating). The existing `ChartScale` takes the max over all curves.
- "Compare with the last brew of this recipe" is a shortcut on the brew detail, the most common comparison.
- Trends: a per-recipe (or per-mode) view with small charts of ratio, total time and rating over the last N brews,
  computed in the ViewModel from Room, no aggregation tables.
- Still no chart library ([ADR 0004](0004-minimal-dependencies.md)); the overlay reuses the detail chart's drawing.

## Consequences

- Same-mode only keeps the axes meaningful (0–40 g against 0–500 g).
- Trends depend on recipes and ratings ([ADR 0018](0018-recipes.md), [ADR 0021](0021-brew-notes.md)), so they come
  last.
- The 300 ms detail target from stage 4 applies to the compare screen with four brews.
