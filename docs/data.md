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

- All fields are required and unknown ones are rejected. `time_to_first_drop_s` is `null` for filter brews;
  `dose_g > 0`; `ended_at_ms > started_at_ms`; at most 20000 samples (may be empty); body at most 4 MiB.
- `id` is a lowercase UUID generated on the phone; the server upserts on it, so a retry never duplicates a brew
  ([ADR 0007](adr/0007-idempotent-upload.md)).
- `201` for a new brew, `200` when it already existed. `400` invalid body, `401` wrong token, `413` too large; none of
  the 4xx are retried. `5xx` and timeouts are retried.
- `samples[].t_ms` is relative to `started_at_ms`.
- "Test connection" sends `GET /healthz` and an authenticated `GET /api/v1/brews`; neither creates data.
- One WorkManager job per brew (`upload-<id>`, tag `upload`), only while a server is set ([ADR 0016](adr/0016-upload-jobs.md)).

## On-device data

| Store | Contents | Stage |
|-------|----------|-------|
| SharedPreferences `settings` | `onboarding_done`, `last_dose_espresso` / `last_dose_filter`; `server_url`, `server_token` (both empty: no uploads); `last_sync_ms`; `last_sync_error`, `last_sync_error_detail`, `last_sync_error_brew` (the last upload error, its `SyncError` kind name) | 2, 3, 5 |
| Room `caparao.db`, table `brews` | One row per brew: `id` (phone UUID), `mode`, `started_at`, `duration_ms`, `dose_grams`, `yield_grams`, `time_to_first_drop_ms`, `flow_avg`, `flow_peak`, `interrupted`, `synced_at` | 3 |
| Room `caparao.db`, table `brew_samples` | The curve: (`brew_id`, `at_ms`) → `weight_grams`, `flow` (smoothed from the weights), at most 10 per second; deleted with its brew | 3 |

The exported schema is in [`app/schemas/`](../app/schemas); every version change needs a migration and a migration test.
No scale address is stored: the app scans each time ([hardware.md](hardware.md)).

All of it is excluded from cloud backups and device transfers.
