# 2. BLE through the platform's BluetoothGatt, without a library

Status: Accepted

## Context

Android BLE libraries (Nordic Kotlin BLE, BLESSED) hide GATT's callback ordering and one-operation-at-a-time rule. The
app talks to one device with one service, two characteristics and a handful of commands.

## Decision

Use `android.bluetooth.BluetoothGatt` directly behind a `ScaleClient` with its own operation queue (one GATT operation
in flight), connect retries, bonding and reconnect backoff. The protocol itself (`DotProtocol`, `FrameDecoder`) is pure
Kotlin with no Android types.

## Consequences

- No dependency to update or audit for BLE ([ADR 0004](0004-minimal-dependencies.md)).
- We own the GATT edge cases (status 133, bonding races, MTU timing); `ScaleClient` is tested against a fake GATT.
- The protocol code is testable on the JVM with recorded frames.
