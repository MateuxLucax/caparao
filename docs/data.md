# Data

## Upload contract

Fixed in stage 5. The schema, the rules and the shared example files live in [`contract/`](../contract/README.md);
the Android client and the Go stub are both tested against them.

```
POST {serverUrl}/api/v1/brews
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "id": "0f8e1c2a-5b7d-4c3e-9a10-2f6d8b4e7c91",
  "mode": "espresso",
  "started_at_ms": 1792931400000,
  "ended_at_ms": 1792931428600,
  "dose_g": 18.0,
  "yield_g": 36.4,
  "time_to_first_drop_s": 6.2,
  "flow_avg_gps": 1.3,
  "flow_peak_gps": 2.1,
  "interrupted": false,
  "samples": [{ "t_ms": 0, "weight_g": 0.0, "flow_gps": 0.0 }]
}
```

- All fields are required and unknown ones are rejected. `mode` is `espresso`, `filter` or `french_press`;
  `time_to_first_drop_s` is `null` for filter and French press brews, which start on the first water; `dose_g > 0`;
  `ended_at_ms > started_at_ms`; at most 20000 samples (may be empty); body at most 4 MiB.
- `id` is a lowercase UUID generated on the phone; the server upserts on it, so a retry never duplicates a brew
  ([ADR 0007](https://github.com/MateuxLucax/caparao/issues/91)).
- `201` for a new brew, `200` when it already existed. `400` invalid body, `401` wrong token, `413` too large; none of
  the 4xx are retried. `5xx` and timeouts are retried.
- `samples[].t_ms` is relative to `started_at_ms`.
- "Test connection" sends `GET /healthz` and an authenticated `GET /api/v1/brews`; neither creates data.
- One WorkManager job per brew (`upload-<id>`, tag `upload`), only while a server is set ([ADR 0016](https://github.com/MateuxLucax/caparao/issues/100)).

## On-device data

| Store | Contents | Stage |
|-------|----------|-------|
| SharedPreferences `settings` | `onboarding_done`, `last_dose_espresso` / `last_dose_filter`; `server_url`, `server_token` (both empty: no uploads); `last_sync_ms`; `last_sync_error`, `last_sync_error_detail`, `last_sync_error_brew` (the last upload error, its `SyncError` kind name) | 2, 3, 5 |
| Room `caparao.db`, table `brews` | One row per brew: `id` (phone UUID), `mode`, `started_at`, `duration_ms`, `dose_grams`, `yield_grams`, `time_to_first_drop_ms`, `flow_avg`, `flow_peak`, `interrupted`, `synced_at` | 3 |
| Room `caparao.db`, table `brew_samples` | The curve: (`brew_id`, `at_ms`) → `weight_grams`, `flow` (smoothed from the weights), at most 10 per second; deleted with its brew | 3 |

The exported schema is in [`app/schemas/`](../app/schemas); every version change needs a migration and a migration test.
No scale address is stored: the app scans each time ([hardware.md](hardware.md)).

## Export

Settings → Export writes `cache/exports/caparao-export.zip` with `brews.json` (the upload contract, curve included),
`brews.csv`, `recipes.csv` and, optionally, `samples.csv`. A brew's detail shares one JSON. `FileProvider` grants the
receiving app read access, so no storage permission is needed and the export uploads nothing by itself.

All of it is excluded from cloud backups and device transfers.

## Weekly widget estimates (#136)

The expanded home-screen widget covers the last seven **local calendar dates**, including today. A brew belongs to the
date of its end instant (`started_at + duration_ms`); interrupted brews count and use their recorded dose and yield. The
query reads `brews` only, not `brew_samples`.

- Beverage volume is an estimate: espresso yield at about 1 g ≈ 1 ml; filter water poured minus about 2 g of retained
  water per gram of dose, clamped to zero. The current model stores poured water for filter brews.
- Caffeine is an estimate of the **beverage brewed**, not caffeine consumed or a health measure: dose × 7 mg/g for
  espresso, or × 11 mg/g for filter. The app does not know coffee variety, roast, decaf status or extraction. These
  product factors assume regular coffee and vary from brew to brew.
- The caffeine factors are estimates, not universal measurements. Arabica espresso studies report roughly 8.4–9.5 mg/g
  of dose ([Ludwig et al. (2014)](https://doi.org/10.1039/c4fo00290c); [Caporaso et al. (2014)](https://doi.org/10.1016/j.foodres.2014.01.020));
  filter results vary by method, roughly 8.4–12.8 mg/g ([Ludwig et al. (2012)](https://doi.org/10.1016/j.foodres.2012.02.023);
  [Angeloni et al. (2019)](https://doi.org/10.1016/j.foodres.2018.10.022)); that Angeloni et al. study reports V60 around
  10.2 mg/g.
  The 7 mg/g espresso value is a conservative product choice; 11 mg/g is a rough central filter estimate. Robusta can
  have about twice the Arabica caffeine per gram in a brewed serving ([Ludwig et al. (2014)](https://doi.org/10.1039/c4fo00290c)).
  [Angeloni et al. (2019)](https://doi.org/10.1016/j.foodres.2018.10.022) measured about 6.9 mg/g for its French-press
  preparation, supporting a separate rounded 7 mg/g estimate when that mode lands. It was one recipe, not a universal
  French-press factor.
- For [Angeloni et al.'s (2019)](https://doi.org/10.1016/j.foodres.2018.10.022) espresso comparison, beverage concentration
  × beverage mass is used: its Table 4 mg/g entries conflict with those values, while the V60 and French-press results
  are internally consistent.
- The 2 g/g retention assumption is approximate: a drip-brewing model uses about 2.1 g/g ([Guinard et al. (2023)](https://doi.org/10.1111/1750-3841.16531)),
  while a full-immersion mass-balance study measured 2.48 ± 0.19 g/g ([Liang et al. (2021)](https://doi.org/10.1038/s41598-021-85787-1)).
  The 2 g/g value is a reasonable estimate, not an exact per-brew measurement. French press will use the same 2 g/g
  volume retention once its mode is added.

Primary sources:

- [Ludwig et al. (2014), *Variations in caffeine and chlorogenic acid contents of coffees: what are we drinking?*](https://doi.org/10.1039/c4fo00290c)
- [Angeloni et al. (2019), *What kind of coffee do you drink? An investigation on effects of eight different extraction methods*](https://doi.org/10.1016/j.foodres.2018.10.022)
- [Caporaso et al. (2014), *Neapolitan coffee brew chemical analysis in comparison to espresso, moka and American brews*](https://doi.org/10.1016/j.foodres.2014.01.020)
- [Ludwig et al. (2012), *Extraction of coffee antioxidants: Impact of brewing time and method*](https://doi.org/10.1016/j.foodres.2012.02.023)
- [Guinard et al. (2023), *A new Coffee Brewing Control Chart relating sensory properties and consumer liking to brew strength, extraction yield, and brew ratio*](https://doi.org/10.1111/1750-3841.16531)
- [Liang et al. (2021), *An equilibrium desorption model for the strength and extraction yield of full immersion brewed coffee*](https://doi.org/10.1038/s41598-021-85787-1)

French press uses its own exhaustive per-mode rule: 7 mg/g caffeine and 2 g/g retained water per gram of dose, as added
with the mode in [#125](https://github.com/MateuxLucax/caparao/issues/125). This supersedes the earlier #136 wording
that French press counts like filter; do not fold it into the weekly-widget change.
