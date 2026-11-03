# Recording guide

Stages 2, 3 and 5 are done in code but have no dated run on the phone, and the detectors have only seen synthetic brews.
Both gaps need the user at the coffee machine: real sessions for the detectors
([#79](https://github.com/MateuxLucax/caparao/issues/79)) and the app scripts on the phone
([#78](https://github.com/MateuxLucax/caparao/issues/78)). This page lists the sessions and the order of the runs; the
in-app recorder, the external capture tool and file formats are in [capture.md](../capture.md).

Use Caparaó's **Settings → Diagnostics** card for these everyday sessions. It records through the app's existing BLE
connection, so the scale readout, detector and normal brew flow keep working. The external
`scripts/capture-session.py` remains available for protocol work; it owns the BLE link and requires Caparaó to be
disconnected while it runs.

## Before the first in-app capture

Open Settings and start recording before placing the cup or dripper, so the file contains an empty scale baseline. Then
return to the brew screen and make the espresso or pour-over as usual. After the summary appears, close the brew screen,
return to Settings and stop recording. The completed session stays on the phone until you choose **Share file**.

Record the coffee label, dose, grind, pump or pour timing, expected start/end/yield and anything unusual in the table
below or on issue #79. Those annotations are separate from the app's detector result. Use the raw trace for context,
and mark an event uncertain when its timing or meaning is unclear. In particular, do not call ambiguous water-only
activity a proven negative.

Recording has a bounded queue and file size. If it stops early, the Settings card marks the file incomplete with the
reason; keep that reason with the session row and do not use it as a complete fixture. Sharing the file is optional and
explicit.

## Sessions

Thirteen captures in total: five espressos, five pour-overs and three false starts. Start and stop one diagnostic file
per row. The generated filename has a timestamp, not the row label; copy the filename into the table so it can be
matched to your independent notes. Keep coffee labels and notes in the guide or issue, never in the diagnostic file.

### Espresso

Start the diagnostic recording, open an espresso brew and place the cup when the brew screen asks. Make the shot as
usual. Keep the cup on the scale after the last drip until the app shows its summary; for `espresso-3`, lift it within
4 s of the last drip so the capture contains the lift before the flat window. Close the brew screen, return to Settings
and stop recording.

The reference grind is your everyday espresso setting; finer and coarser are whole steps on your grinder. What matters
is that the five shots differ, not the exact numbers.

| # | Label | Dose | Grind | Shot | What it covers |
|---|-------|------|-------|------|----------------|
| 1 | `espresso-1` | 18 g | reference | pump for about 28 s, cup stays | the baseline: a 1:2 shot that ends on the flat weight |
| 2 | `espresso-2` | 18 g | two steps finer | pump for about 35 s, cup stays | a slow, choked shot: a small yield and a long rise |
| 3 | `espresso-3` | 18 g | two steps coarser | pump for about 20 s, lift the cup | a fast shot that ends on the lift, not the flat window |
| 4 | `espresso-4` | 16 g | reference | pump for about 24 s, cup stays | a smaller dose: a shorter shot |
| 5 | `espresso-5` | 20 g | one step coarser | pump for about 30 s, cup stays | a larger dose: a longer shot |

### Filter

Start the diagnostic recording, return to the scale screen, then put the dripper and server on the scale and open a
filter brew. The file includes the empty baseline, the dripper/server and the app's tare command. Pour your usual recipe
and time the pours with a stopwatch. Tap Done when finished, close the summary, return to Settings and stop recording.

The ratio is the plan, not a number you can watch. Pour the way you normally do and the file records what landed.

| # | Label | Dose | Grind | Pour | What it covers |
|---|-------|------|-------|------|----------------|
| 1 | `pour-1` | 15 g | reference | 1:16, bloom at 2× the dose for 40 s, then two pours | the everyday pour-over: a bloom pause and the lift |
| 2 | `pour-2` | 20 g | one step coarser | 1:15, bloom, then one continuous pour | a faster bed and a shorter brew |
| 3 | `pour-3` | 12 g | two steps finer | 1:17, one slow pour | a fine grind: the weight plateaus longer between pours |
| 4 | `pour-4` | 30 g | two steps coarser | 1:16, bloom, then three pours | a big batch: the lift is a much larger drop |
| 5 | `pour-5` | 15 g | reference | 1:16, three pours with 20 s waits | long waits that must not end the brew |

### False starts

These three are false-start cases with no coffee. Start recording before the event, open the matching brew mode, perform
the action, then close the brew screen and stop from Settings. Record what happened and when. If a water-only case is
ambiguous, mark it uncertain; do not label it a proven negative.

| Label | Setup | Event |
|-------|-------|-------|
| `false-cup-tap` | espresso; cup on the scale, still | tap the cup with a spoon several times, then remove it |
| `false-scale-bump` | filter; dripper and server on the scale | knock the scale lightly, then hard; lift the dripper and set it back within 5 s; then remove everything |
| `false-water-first` | filter; dripper and server on the scale, no coffee | rinse the paper filter so the water lands in the server; lift the dripper |

## Fill one row per session

Record expected values from your independent observation of the brew, not from the app's detector result. The raw file
can be inspected with [Read the weights](../capture.md#read-the-weights) to add context, but keep the detector's result
out of the expected columns. If you cannot tell when an event occurred, write `uncertain` instead of guessing, then:

- **Baseline (g)**: the steady weight in the file just before the brew — the cup, or the dripper and server.
- **Start (s)**: the first reading where the weight leaves the baseline and stays above it: the first drop, or the first
  water.
- **End (s)**: the last reading where the weight was still rising, before it flattened or dropped: the last drip, or
  the moment the dripper lift began.
- **Yield (g)**: the highest weight of the brew minus the baseline; for a filter, the water poured.
- **What happened**: the dose and grind that went in, the pump or pour times, and anything that differed from the plan.

Times are seconds from the start of the session, as the reading command prints them. One decimal is enough. A false
start with no brew gets `—` in Baseline, Start, End and Yield.

| Label | File | What happened | Baseline (g) | Start (s) | End (s) | Yield (g) |
|-------|------|---------------|--------------|-----------|---------|-----------|
| `espresso-1` | | | | | | |
| `espresso-2` | | | | | | |
| `espresso-3` | | | | | | |
| `espresso-4` | | | | | | |
| `espresso-5` | | | | | | |
| `pour-1` | | | | | | |
| `pour-2` | | | | | | |
| `pour-3` | | | | | | |
| `pour-4` | | | | | | |
| `pour-5` | | | | | | |
| `false-cup-tap` | | | — | — | — | — |
| `false-scale-bump` | | | — | — | — | — |
| `false-water-first` | | | — | — | — | — |

Captures stay in the git-ignored `spike-captures/` until they are promoted: review a file, then copy it into
`app/src/test/resources/sessions/`, whose [README](../../app/src/test/resources/sessions/README.md) explains the fixture
table. Never commit a raw capture. Fill a copy of the table and hand it over on
[#79](https://github.com/MateuxLucax/caparao/issues/79); that is what unblocks the tuning
([#122](https://github.com/MateuxLucax/caparao/issues/122)).

## The #78 run order

The app scripts run on their own coffees, with no capture running. The order is `scale.md`, `brews.md`, `sync.md`: a
brew needs the scale connected first, and sync needs a brew to upload.

1. **Install the build under test.** Connect the phone by USB with USB debugging on and run, from the repository root:

   ```bash
   adb uninstall io.github.mateuxlucax.caparao.debug   # "Failure" when it was not installed: that is fine
   ./gradlew :app:installDebug
   ```

   That is the debug package ("Caparaó dev"), installed next to the release app and with its own data. With an emulator
   also attached, set `ANDROID_SERIAL` to the phone's serial from `adb devices` before the install.

2. **[scale.md](scale.md)** — the first run, from a fresh install, as the script asks. Steps 6–9 turn the Dot off and
   on, toggle Bluetooth and pair the Dot elsewhere; keep the phone's Bluetooth settings within reach.
3. **[brews.md](brews.md)** — the same install, no uninstall. One real espresso (steps 1–6), one real filter (step 7)
   and the short cases in steps 8–10: a cup lifted mid-shot, the Dot turned off mid-brew and a discard.
4. **[sync.md](sync.md)** — uninstall and install again, because step 1 opens onboarding with the server. The brews
   from the last run are gone with the uninstall; record a new espresso in step 3, type notes on the summary and tap
   Save, so the upload carries them (#78 asks for a brew with notes).

sync.md is written against the stub on a computer. #78 also asks for the deployed exporter: after the stub run, set it
in Settings and upload one more espresso with notes (its URL and token never go in the repository). Steps 5–7 need a
server you can stop or re-key, so they stay on the stub.

Before the run, the Dot may still be bonded to the Mac from the recordings. Hold its button until the LED blinks fast
and pair it with the phone again, as [capture.md](../capture.md#record) says.

### Record each run

Under the script's `## Runs`, newest first, add one heading and one table. For example:

```
### 2026-11-02 · 0.0.0-dev (1b80830) · Galaxy S23 (SM-S916B), Android 17

| Step | Result |
|------|--------|
| 1. Open the app | Pass |
| 2. Allow Bluetooth | Pass: connected in 8 s |
| 8. Turn Bluetooth off | Fail: the card kept "Connection lost" |
```

Every step gets a row, `Pass` or `Fail` plus the deviation; a step that was not run says why. Open an issue for every
failure and link it in its cell. A local build shows `0.0.0-dev` in Settings for every commit, so add the short commit
from `git rev-parse --short HEAD` next to the version.
