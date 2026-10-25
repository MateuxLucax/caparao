# 1. Our own minimal app instead of Timemore's

Status: Accepted

## Context

Timemore's official app connects to the Dot and records brews, but it is heavy, shows far more than a daily brew
needs, and keeps the data to itself: there is no export. The Dot's BLE protocol is public
([open-scale-protocol](https://github.com/TIMEMORE-COFFEE/open-scale-protocol)) and already implemented by several
open-source apps (Beanconqueror, dotscale, Pourista), so a purpose-built client is feasible.

## Decision

Build a small native Android app that does three things: show the scale live, record filter and espresso brews, and
optionally send them to the user's own server.

## Consequences

- Small, auditable code base; brews go only to the server the user configures.
- The Dot pairs with one phone at a time, so this app and Timemore's compete for it (re-pairing needs the scale's
  button).
- We maintain the protocol code ourselves and follow firmware changes.
