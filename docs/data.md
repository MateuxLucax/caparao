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

## On-device data

| Store | Contents | Stage |
|-------|----------|-------|
| SharedPreferences `settings` | Paired scale address, last dose per mode, server URL and token | 2, 3, 5 |
| Room `brews` | One row per brew: id, mode, times, dose, yield, summary, `synced_at` | 3 |
| Room `brew_samples` | Weight and flow curve, ~10 points per second | 3 |

All of it is excluded from cloud backups and device transfers.
