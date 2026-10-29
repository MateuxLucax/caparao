# 23. Watch companion: a Wear OS mirror fed by the phone

Status: Proposed

## Context

The user wants to see the brew on their wrist: weight, timer and maybe a few more values, without looking at the
phone on the counter. The phone is a Galaxy S23, and the watch is a Galaxy Watch Ultra (Wear OS 5, API 34, round 480×480 display). The scale talks to
one central at a time over BLE ([ADR 0002](0002-native-ble.md)), and the app connects only while it is open
([ADR 0008](0008-connect-while-open.md)). What exactly the watch shows is still open and will be refined.

## Decision

- A Wear OS module (`wear/`) in this repository, Kotlin and Compose for Wear OS, sharing pure models with the app.
  Same minSdk policy as the watch allows; no other watch platforms.
- The phone stays the only BLE central. The watch never talks to the scale: it mirrors what the phone already
  computes, so detection, recipes and uploads keep one source of truth.
- Transport: the Wearable Data Layer (`MessageClient`) from the phone's brew screen, sending a small snapshot (weight,
  elapsed time, flow, brew state, and for guided brews the current step and pace from
  [ADR 0019](0019-guided-brew.md)) at most about 5 times a second. Messages, not `DataClient` items, since stale
  frames have no value.
- First version on the watch: one screen with weight and timer, flow and step when present, and a tare button sent
  back to the phone. An ongoing activity keeps it on the wrist face while a brew runs.
- Vibration alerts from [ADR 0019](0019-guided-brew.md) can also fire on the watch; a Settings toggle chooses phone,
  watch or both.
- Offline and optional: the app works the same with no watch paired; nothing is stored on the watch.

## Consequences

- Adds the Play Services Wearable dependency, an exception to [ADR 0004](0004-minimal-dependencies.md) limited to the
  phone's brew screen and the `wear/` module.
- The watch is useful only while the phone app is open and connected, which matches ADR 0008.
- Release builds a second APK signed with the same key ([ADR 0012](0012-sideloaded-apk-releases.md)); sideloading on
  the watch goes through adb over Wi-Fi.
- Tested with `ReplayScaleSource` on a Wear OS emulator paired to the phone emulator; the snapshot mapping is a pure
  function with JVM tests.
- Open for refinement: which values fit the round screen, a tile or complication, and starting a brew from the watch.
