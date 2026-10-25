# Brews and history (stages 3 and 4)

Automated: `scripts/check.sh` runs the detector tests (`EspressoDetectorTest`, `FilterDetectorTest`, synthetic
sessions for every case in the roadmap), `FlowSmootherTest`, `BrewSummaryTest`, `BrewViewModelTest`,
`ReplayedEspressoTest` (the synthetic espresso through replay, decoder, tare and detector to a saved brew),
`BrewsViewModelTest` and `ChartScaleTest`. On the emulator: `BrewDaoTest` (real SQLite: order, cascade, atomic
insert, unsynced) and `AppTest` (Espresso opens the dose step; leaving before the first drop saves nothing).

Manual, on a phone with the Dot connected:

1. Home → Espresso. The dose step shows the live weight; weigh 18 g of beans and tap Use weight, or type it. Next.
2. Put the cup on the scale and leave it: the app tares by itself (the weight goes to 0.0 g in about a second).
3. Start the shot: the timer starts with the first drops, the ring fills with the flow, the ratio counts up.
4. Stop the pump: about 4 s after the flow stops, the brew is saved. Compare yield and time with what you saw (yield
   ±0.3 g, start ±0.5 s, end ±1 s).
5. The screen stayed on the whole time.
6. Brews tab: the espresso is listed with dose → yield, time, ratio and "Only on this phone". Open it: the numbers and
   the weight and flow curves. Delete asks first, then removes it.
7. Filter: Home → Filter, skip the dose. Put dripper and server on the scale: the app tares. Pour with a bloom pause;
   the pause does not end it. Lift the dripper: about 5 s later the brew is saved with the water poured as its yield.
8. Lift the cup in the middle of an espresso: the brew ends at the lift with the weight before it.
9. Turn the Dot off during a brew: the brew is saved as interrupted.
10. Discard on the saved screen removes the brew from the list.

Real espresso and filter recordings (probe) are still needed to tune the thresholds; see
[hardware.md](../hardware.md) and [spike.md](../spike.md).

## Runs

No device run yet.
