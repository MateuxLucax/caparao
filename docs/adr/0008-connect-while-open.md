# 8. Connect to the scale only while the app is open

Status: Accepted

## Context

A brew takes from 30 seconds to a few minutes, with the phone next to the scale. Keeping a BLE connection in the
background needs a foreground service with a permanent notification and drains battery.

## Decision

Connect when the app comes to the foreground, reconnect with backoff (1 to 10 s) while it is open, and disconnect when
it leaves. During a brew the screen stays on (`FLAG_KEEP_SCREEN_ON`). No foreground service.

## Consequences

- Simple lifecycle, no notification, no background battery use.
- Locking the phone or switching apps mid-brew interrupts recording; the brew is saved and marked interrupted.
