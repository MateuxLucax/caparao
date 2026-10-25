# Scale (stage 2)

Automated: `scripts/check.sh` (`DotProtocolTest`, `FrameDecoderTest`, `RecordedSessionTest`, `ScaleClientTest`,
`ReplayScaleSourceTest`, the Home and Onboarding ViewModel tests) and, on the emulator, `AppTest` (onboarding with a
replayed session, live Home).

Manual, on a phone with the Timemore Dot. Start from a fresh install (`adb uninstall io.github.mateuxlucax.caparao.debug` for a debug build),
with the Dot on and forgotten in the phone's Bluetooth settings.

1. Open the app: the welcome animation and text show; Get started goes to the Bluetooth step.
2. Allow Bluetooth: the system asks for Nearby devices; after allowing, the scale step searches, connects and shows
   the live weight. Time from Allow to "Scale connected": < 30 s, including the pairing prompt.
3. Put a known weight on the scale: the app shows the same value as the official app (or a kitchen reference) ±0.1 g.
4. Finish: Home shows "Scale connected", the battery %, weight and flow live.
5. Tare: the weight goes to 0.0 g in < 1 s. Start / Pause / Reset: the timer under the weight follows.
6. Turn the Dot off: Home shows "Connection lost" and retries; turn it on: it reconnects by itself, no tap needed.
7. Turn Bluetooth off in quick settings: the error card offers "Turn on Bluetooth", which opens the system dialog;
   after turning it on, the app connects again.
8. Put the app in the background for a minute and back: it reconnects (the Dot is released while hidden).
9. Pair the Dot with another phone or the Timemore app, then open Caparaó: "The scale refused to pair" (or "Scale
   not found") with the reset hint. Hold the Dot's button until the light blinks fast: the next attempt connects.
10. On an error, Copy debug log: the pasted text has no Bluetooth address or device name.

## Runs

No device run yet.
