# Foundation (stage 1)

Automated: `scripts/check.sh` (unit tests, lint, build, secret scan) and `./gradlew connectedDebugAndroidTest`
(`AppTest`: every tab opens its screen).

Manual, on a phone or emulator:

1. Install the debug APK. The launcher shows the cup-on-scale icon; with themed icons on (Android 13+), the monochrome
   version.
2. Open the app: the Home tab shows "Scale not connected", 0.0 g, 00:00, disabled Tare and Timer, and the Filter and
   Espresso buttons.
3. Switch to Brews and Settings and back: each tab shows its screen.
4. Switch the system to dark theme: the colors follow; nothing is unreadable.
5. Switch the app language to Português (Settings > System > Languages > App languages): every text is in Portuguese,
   weights use a comma (0,0 g).

## Runs

### 2026-10-25 · 0.0.0-dev · Pixel 10a emulator, Android 17

| Step | Result |
|------|--------|
| `scripts/check.sh` | Pass (22 s incremental, ~1.5 min clean) |
| `connectedDebugAndroidTest` (`AppTest`) | Pass |
| 1. Launcher icon | Pass: cup on the scale with the red LED, inside the mask |
| 2. Home disconnected | Pass |
| 3. Tabs | Pass (covered by `AppTest`) |
| 4. Dark theme | Pass |
| 5. Portuguese | Pass: "Balança desconectada", "0,0 g", tabs Início / Dados / Config |
| Themed (monochrome) icon | Not checked |
