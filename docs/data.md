# Data

## Upload contract (draft, finalized in stage 5)

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
  "samples": [{ "t_ms": 0, "weight_g": 0.0, "flow_gps": 0.0 }]
}
```

- `id` is generated on the phone; the server upserts on it, so a retry never duplicates a brew
  ([ADR 0007](adr/0007-idempotent-upload.md)).
- `201` for a new brew, `200` when it already existed. `401` for a wrong token; other `4xx` are not retried.
- `samples[].t_ms` is relative to `started_at_ms`.

The exact field list, limits and error bodies are fixed in stage 5, together with the shared files in `contract/`.

## On-device data

| Store | Contents | Stage |
|-------|----------|-------|
| SharedPreferences `settings` | Paired scale address, last dose per mode, server URL and token | 2, 3, 5 |
| Room `brews` | One row per brew: id, mode, times, dose, yield, summary, `synced_at` | 3 |
| Room `brew_samples` | Weight and flow curve, ~10 points per second | 3 |

All of it is excluded from cloud backups and device transfers.
