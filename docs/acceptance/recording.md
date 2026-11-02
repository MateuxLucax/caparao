# Recording guide

Stages 2, 3 and 5 are done in code but have no dated run on the phone, and the detectors have only seen synthetic brews.
Both gaps need the user at the coffee machine: real sessions for the detectors
([#79](https://github.com/MateuxLucax/caparao/issues/79)) and the app scripts on the phone
([#78](https://github.com/MateuxLucax/caparao/issues/78)). This page lists the sessions and the order of the runs; the
capture tool, its setup and the format of a file are in [capture.md](../capture.md).

**A coffee is either a recording or an app run, never both.** `scripts/capture-session.py` owns the Bluetooth link while
it runs, so Caparaó cannot be connected to the same brew. Record on one set of coffees and run the app scripts on
another.

## Before the first capture

Close or force-stop Caparaó and the Timemore app before every capture: the script takes the link, and the Dot accepts
one connection. The [setup](../capture.md#setup-macos) (the virtual environment and the Bluetooth permission) is done
once, and the steps around the script are in [Record](../capture.md#record).

Nothing shows the weight while a capture runs: the Dot has no display and the app is closed. Weigh the dose and set the
grind before you start, brew by time as you usually do, and read the numbers from the file afterwards.

Do the recordings in one sitting if you can, with the app closed: the Dot stays bonded to the Mac between captures and
the phone only needs to pair again for the app run.

## Sessions

Thirteen captures in total: five espressos, five pour-overs and three false starts. Each session is one capture with
the label from its row: `.venv/bin/python scripts/capture-session.py "<label>"`. Labels are lowercase words so they
survive in the file name; keep them, so a file can be matched to its row. One capture covers one coffee: a dropped link
ends the file instead of reconnecting.

### Espresso

Put the cup on the scale once the script prints `connected; recording until Ctrl+C`, and leave it there while you finish
the puck. Stop the pump by time and leave the cup on the scale for 10 s before Ctrl+C: the app ends the brew 4 s after
the last drip, so those seconds have to be in the file. `espresso-3` is the exception: lift the cup within 4 s of the
last drip, so the file has the lift before the flat window, then Ctrl+C after 5 s.

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

Put the dripper and server on the scale before the capture starts, so the file has them from the first reading;
replayed, the app tares them off. Pour your usual recipe and time the pours with a stopwatch, since the Dot's weight
cannot be watched. End every session by lifting the dripper and server off the scale: a capture cannot record the Done
tap. Wait 5 s after the lift before Ctrl+C.

The ratio is the plan, not a number you can watch. Pour the way you normally do and the file records what landed.

| # | Label | Dose | Grind | Pour | What it covers |
|---|-------|------|-------|------|----------------|
| 1 | `pour-1` | 15 g | reference | 1:16, bloom at 2× the dose for 40 s, then two pours | the everyday pour-over: a bloom pause and the lift |
| 2 | `pour-2` | 20 g | one step coarser | 1:15, bloom, then one continuous pour | a faster bed and a shorter brew |
| 3 | `pour-3` | 12 g | two steps finer | 1:17, one slow pour | a fine grind: the weight plateaus longer between pours |
| 4 | `pour-4` | 30 g | two steps coarser | 1:16, bloom, then three pours | a big batch: the lift is a much larger drop |
| 5 | `pour-5` | 15 g | reference | 1:16, three pours with 20 s waits | long waits that must not end the brew |

### False starts

These three are negatives: no coffee on the scale, and nothing should be saved. Note when the event happened in the
`What happened` column, so the replay can be checked at that moment.

| Label | Setup | Event |
|-------|-------|-------|
| `false-cup-tap` | espresso; cup on the scale, still | tap the cup with a spoon several times, then remove it |
| `false-scale-bump` | filter; dripper and server on the scale | knock the scale lightly, then hard; lift the dripper and set it back within 5 s; then remove everything |
| `false-water-first` | filter; dripper and server on the scale, no coffee | rinse the paper filter so the water lands in the server; lift the dripper |

## Fill one row per session

The expected values are the brew as the scale recorded it, not what the app computed. Read them from the file with
[Read the weights](../capture.md#read-the-weights), then:

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
