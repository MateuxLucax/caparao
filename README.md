<h1 align="center">Caparaó</h1>

<p align="center">
  <img src="https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose">
</p>

<p align="center"><b>Brew better coffee with the scale you already have.</b></p>

Caparaó turns the [Timemore Dot](https://www.timemore.com) into a brewing guide. It walks you through espresso, filter
and French press by weight and time, remembers what each coffee needs, and tells you what to change after you taste
the cup. No account and no ads; your brews stay on your phone unless you connect your own server.

<p align="center">
  <b>Android 12+ · Wear OS 3+ · English and Brazilian Portuguese</b><br>
  On Google Play and as signed APKs in <a href="../../releases">Releases</a> · <a href="PRIVACY.md">Privacy policy</a>
</p>

## Dial in every coffee

A new bag rarely brews like the last one. Caparaó keeps the grind, water temperature and dose that work for each
coffee in each recipe, and moves them one step at a time from what you tasted.

<table>
<tr>
<td width="25%" align="center" valign="top"><img src="assets/demo/coffee-step.png" width="200" alt="Coffee step of a filter brew: the picked bag shows its adjusted grind and dose beside the recipe's values"><br><b>Pick the coffee</b><br>Its grind, temperature and dose come with it. One tap brews as the recipe says instead.</td>
<td width="25%" align="center" valign="top"><img src="assets/demo/rating.png" width="200" alt="How was it? screen with the taste choice, the bag's tasting notes and the star rating"><br><b>Rate when you taste it</b><br>Sour, balanced or bitter, which of the bag's notes showed up, and a rating. A quiet reminder asks a few minutes after the brew.</td>
<td width="25%" align="center" valign="top"><img src="assets/demo/suggestion.png" width="200" alt="Suggestion screen proposing a coarser grind, with the brew time against the target and the taste as reasons"><br><b>Get one clear change</b><br>Grind or temperature, one step, with the reason. Save it for that coffee or leave it.</td>
<td width="25%" align="center" valign="top"><img src="assets/demo/coffee-detail.png" width="200" alt="Coffee detail with the average rating, the taste it tends to, the notes found and its adjustments per recipe"><br><b>See how it is going</b><br>Average rating, the taste it tends to, how often each note showed up, and its adjustments.</td>
</tr>
</table>

## Brew with a guide

<table>
<tr>
<td width="25%" align="center" valign="top"><img src="assets/demo/home-live.png" width="200" alt="Home in Manual mode showing the live weight, timer and flow"><br><b>Live scale</b><br>Weight, timer and flow in real time, with tare and timer controls.</td>
<td width="25%" align="center" valign="top"><img src="assets/demo/guide-pour.png" width="200" alt="Guided filter pour showing the target weight and flow pace"><br><b>Guided pours</b><br>Bloom, pours and waits by weight and time, with ahead or behind feedback and alerts.</td>
<td width="25%" align="center" valign="top"><img src="assets/demo/espresso-live.png" width="200" alt="Espresso extracting with its yield, ratio and timer"><br><b>Automatic espresso</b><br>Tares on the cup, starts on the first drop and stops when the flow does.</td>
<td width="25%" align="center" valign="top"><img src="assets/demo/french-press.png" width="200" alt="French press press-and-serve step with the timer and press illustration"><br><b>French press</b><br>Water, steep timer and the press reminder, with a guided finish.</td>
</tr>
</table>

## Keep what works

<table>
<tr>
<td width="25%" align="center" valign="top"><img src="assets/demo/recipe-editor.png" width="200" alt="Recipe editor with dose, ratio, grind, temperature, ideal time and a timeline of the steps"><br><b>Your recipes</b><br>Built-in espresso, V60, Melitta and French press recipes, and your own, with a timeline of the steps.</td>
<td width="25%" align="center" valign="top"><img src="assets/demo/adjustment.png" width="200" alt="Adjustment screen for one coffee and recipe, with grind, temperature and dose steppers and the history of changes"><br><b>Every change, explained</b><br>What each coffee changes in a recipe, and where each change came from.</td>
<td width="25%" align="center" valign="top"><img src="assets/demo/brews.png" width="200" alt="Brews list with a brew to rate on top and rated brews showing coffee, recipe, grind, taste and stars"><br><b>Every brew</b><br>What is waiting for a rating first, then coffee, recipe, grind, taste and stars. Filter by coffee.</td>
<td width="25%" align="center" valign="top"><img src="assets/demo/data.png" width="200" alt="Saved espresso details with the weight and flow chart"><br><b>Curves</b><br>Weight and flow curves for every brew.</td>
</tr>
</table>

## And also

- **On your wrist:** the Wear OS app mirrors the scale and the current pour, with the step, the pace, tare and timer
  buttons, a vibration on each step and a finish alert.
- **Coffee shelf:** bags with roaster, origin, roast date, what is left and a tasting profile. Roasters, origins and
  notes you used before are suggested as you type, and opening another bag of the same coffee brings its adjustments
  along.
- **Scale battery:** the Dot's battery percentage sits in the top bar while it is connected, and turns red at 20%.
- **Home-screen widget:** the last brew, with a shortcut to start the next one.
- **Offline first:** no account and no server. The rating reminder is a local notification.
- **Your data, your server:** optionally, each brew goes to any server that follows the
  [exporter's upload contract](https://github.com/MateuxLucax/timemore-dot-prometheus-exporter/blob/main/testdata/contract/README.md),
  such as [timemore-dot-prometheus-exporter](https://github.com/MateuxLucax/timemore-dot-prometheus-exporter), and from
  there to Prometheus and Grafana. Coffees, adjustments, taste and tasting notes never leave the phone.
- **Material 3 Expressive:** Roboto Flex and Material You colors, in light and dark.
- **Label reading:** Coffee bag photos and recognized text are processed on-device and deleted after reading. Google says ML Kit does not send inputs or results to its servers, but may contact Google for fixes, model updates and accelerator compatibility, and sends API performance and utilization metrics; see [ML Kit privacy terms](https://developers.google.com/ml-kit/terms).

The screens above are emulator captures with a scripted scale standing in for the Dot.

## Troubleshooting

| What you see | What to do |
|---|---|
| "Scale not found" | Turn the Dot on and bring it closer. It connects to one phone at a time: close the Timemore app or another phone using it. |
| "Scale won't pair" | The Dot remembers one phone. Hold its button (under the scale) until the light blinks fast, then tap Retry. |
| "Bluetooth not allowed" | App settings → Permissions → Nearby devices → Allow. |
| The espresso doesn't start | Put the cup down and leave it still for a second: the app tares, then waits for the first drops. If nothing happens after a few seconds, tap "Tare now". |
| The espresso doesn't end | It ends about 4 s after the weight stops rising. Tap Stop to end it yourself. |
| Brews stay "Only on this phone" | Brews tab shows the last upload error and its fix. Test the server in Settings. |
| Server on your home network times out | Android 17 needs the local network permission: allow it when the app asks, or in App settings → Permissions → Nearby devices. |
| "Wrong token" | The token in Settings must match the server's `AUTH_TOKEN`. |

The connection screen has **Copy debug log** for bug reports; it never contains the scale's address or name.

## Architecture

```mermaid
flowchart LR
    dot["Timemore Dot<br/>BLE"] -->|"weight, flow, timer"| app["Caparaó<br/>phone app"]
    app -->|"tare, timer, battery"| dot
    app <-->|"Data Layer"| watch["Wear OS<br/>companion"]
    app --> room[("Room<br/>brews, curves, recipes")]
    room -.->|"optional: POST /api/v1/brews"| srv["your server<br/>(exporter)"]
    srv -.-> prom[("Prometheus")] -.-> graf["Grafana"]
```

- **Kotlin, Jetpack Compose and Material 3**, one ViewModel per screen and manual dependency injection.
- **Layers, one way:** `model ← data ← brew ← sync ← ui`, enforced by a test.
  - `data`: the Dot's BLE protocol (frame decoder, CRC, handshake, reconnect with backoff), Room storage, the upload client.
  - `brew`: pure brew logic fed with timed readings: espresso and filter detectors, flow smoothing, French press
    finish tracking and recipe step tracking.
  - `sync`: WorkManager uploads with retries and readable errors.
  - `ui`: onboarding, home, guided brew, recipes, brews and settings.
- **Modules:** `app` (phone), `wear` (watch) and `wearlink` (shared watch snapshot).
- **Tested against real data:** detectors replay sessions recorded from a real Dot; screens are covered by Robolectric
  tests; `scripts/e2e.sh` runs the app against the pinned exporter on an emulator.

Decisions and pending work live in the [issues](https://github.com/MateuxLucax/caparao/issues), where every decision
lives.

## License

[MIT](LICENSE)
