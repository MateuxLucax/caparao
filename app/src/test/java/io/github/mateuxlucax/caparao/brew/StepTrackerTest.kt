package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.data.scale.FrameDecoder
import io.github.mateuxlucax.caparao.data.scale.ScaleMessage
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.StepKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The guide against synthetic pour-overs (no real filter brew is recorded yet; the thresholds come from the two brews
 * on the exporter, docs/adr/0019-guided-brew.md) and the synthetic filter replay session.
 */
class StepTrackerTest {
    /** A V60 in four pours, fixed here so the guide's tests don't move with the built-in recipes. */
    private val v60 = listOf(
        RecipeStep(StepKind.BLOOM, 50.0, 0),
        RecipeStep(StepKind.POUR, 100.0, 45_000),
        RecipeStep(StepKind.POUR, 150.0, 85_000),
        RecipeStep(StepKind.POUR, 300.0, 120_000),
    )

    /** Feeds a pour-over built from [session], from the first water, and keeps every event with its time. */
    private fun run(steps: List<RecipeStep>, session: SyntheticSession): Pair<StepTracker, List<Pair<Long, StepEvent>>> {
        val tracker = StepTracker(steps)
        val smoother = FlowSmoother()
        val events = session.readings.flatMap { r ->
            val flow = smoother.add(r)
            tracker.update(r.atMs, r.reading.weightGrams, flow).map { r.atMs to it }
        }
        return tracker to events
    }

    /** The V60 poured on time: bloom to 52 g, then 101, 151 and 302 g at 0:45, 1:25 and 2:00, plateaus between. */
    private fun onTimeV60() = SyntheticSession()
        .ramp(0.0, 52.0, 10.0, noise = 0.1).hold(52.0, 35.0, noise = 0.1)
        .ramp(52.0, 101.0, 12.0, noise = 0.1).hold(101.0, 28.0, noise = 0.1)
        .ramp(101.0, 151.0, 12.0, noise = 0.1).hold(151.0, 23.0, noise = 0.1)
        .ramp(151.0, 302.0, 30.0, noise = 0.1).hold(302.0, 60.0, noise = 0.1)

    @Test
    fun stepsAdvanceOnWeightAndEveryPourIsOnTime() {
        val (tracker, events) = run(v60, onTimeV60())
        val reached = events.filter { it.second is StepEvent.Reached }.map { (it.second as StepEvent.Reached).index }
        assertEquals(listOf(0, 1, 2, 3), reached)
        assertTrue(events.any { it.second == StepEvent.AllDone })
        assertEquals(listOf(1, 2, 3), events.mapNotNull { (it.second as? StepEvent.Started)?.index })
        // Each target is reached within 3 g: the 100 g step ends at 97 g, partway up the ramp.
        val reached100At = events.first { it.second == StepEvent.Reached(1) }.first
        assertEquals(45_000 + 12_000 * (97.0 - 52) / (101 - 52), reached100At.toDouble(), 300.0)
        assertTrue(tracker.state.allDone(v60.size))
    }

    @Test
    fun theCountdownTicksTheLastFiveSecondsBeforeAPour() {
        val (_, events) = run(v60, onTimeV60())
        val ticks = events.filter { (it.second as? StepEvent.Countdown)?.index == 1 }
        assertEquals(listOf(5, 4, 3, 2, 1), ticks.map { (it.second as StepEvent.Countdown).secondsLeft })
        assertEquals(40_000.0, ticks.first().first.toDouble(), 200.0)
    }

    @Test
    fun nearTargetComesOnceBeforeEachPourIsReached() {
        val (_, events) = run(v60, onTimeV60())
        val order = events.map { it.second }.filter { it is StepEvent.NearTarget || it is StepEvent.Reached }
        assertEquals(
            listOf(0, 1, 2, 3).flatMap { listOf(StepEvent.NearTarget(it), StepEvent.Reached(it)) },
            order,
        )
    }

    @Test
    fun paceIsMeasuredWhenAPourBegins() {
        // The second pour begins 10 s late, the third 8 s early.
        val session = SyntheticSession()
            .ramp(0.0, 50.0, 10.0).hold(50.0, 45.0)
            .ramp(50.0, 100.0, 10.0)
        val tracker = StepTracker(v60)
        val smoother = FlowSmoother()
        val paces = mutableListOf<StepState>()
        for (r in session.readings) {
            tracker.update(r.atMs, r.reading.weightGrams, smoother.add(r))
            paces += tracker.state
        }
        val pour1 = paces.filter { it.index == 1 && it.pace != null }
        assertEquals(listOf(Pace.BEHIND), pour1.map { it.pace }.distinct())
        assertEquals(10_000.0, pour1.first().paceMs!!.toDouble(), 1_000.0)

        val early = SyntheticSession()
            .ramp(0.0, 50.0, 10.0).hold(50.0, 25.0)
            .ramp(50.0, 75.0, 5.0)
        val (t2, _) = run(v60, early)
        assertEquals(Pace.AHEAD, t2.state.pace)
        assertEquals(-10_000.0, t2.state.paceMs!!.toDouble(), 1_000.0)
    }

    @Test
    fun aContinuousPourIntoTheNextStepCountsFromWhenItGoesOn() {
        // No rest after the bloom: the 100 g pour begins when the weight is clearly past 50 g.
        val session = SyntheticSession().ramp(0.0, 100.0, 20.0)
        val (tracker, events) = run(v60, session)
        assertTrue(events.any { it.second == StepEvent.Reached(1) })
        assertEquals(2, tracker.state.index)
    }

    @Test
    fun overshootIsAHintAndNeverBlocks() {
        val session = SyntheticSession().ramp(0.0, 58.0, 10.0).hold(58.0, 5.0)
        val (tracker, _) = run(v60, session)
        assertEquals(1, tracker.state.index)
        assertEquals(8.0, tracker.state.overshootGrams!!, 0.11)
        // Within 3 g it is not worth a word.
        val (onTarget, _) = run(v60, SyntheticSession().ramp(0.0, 52.0, 10.0).hold(52.0, 5.0))
        assertNull(onTarget.state.overshootGrams)
    }

    @Test
    fun aWaitEndsWhenTheNextStepIsDueWithACountdown() {
        val steps = listOf(
            RecipeStep(StepKind.BLOOM, 45.0, 0),
            RecipeStep(StepKind.WAIT, null, 10_000),
            RecipeStep(StepKind.SWIRL, null, 30_000),
            RecipeStep(StepKind.POUR, 240.0, 40_000),
        )
        val session = SyntheticSession().ramp(0.0, 45.0, 8.0).hold(45.0, 40.0)
        val (tracker, events) = run(steps, session)
        val started = events.filter { it.second is StepEvent.Started }
        // The wait is current once the bloom is reached, and ends at 0:30, when the swirl is due; the swirl at 0:40.
        assertEquals(listOf(1, 2, 3), started.map { (it.second as StepEvent.Started).index })
        assertEquals(30_000.0, started[1].first.toDouble(), 100.0)
        assertEquals(40_000.0, started[2].first.toDouble(), 100.0)
        assertEquals(listOf(5, 4, 3, 2, 1), events.filter { (it.second as? StepEvent.Countdown)?.index == 1 }.map { (it.second as StepEvent.Countdown).secondsLeft })
        assertEquals(3, tracker.state.index)
    }

    @Test
    fun theWaitProgressFollowsTheClock() {
        val steps = listOf(RecipeStep(StepKind.BLOOM, 45.0, 0), RecipeStep(StepKind.WAIT, null, 0), RecipeStep(StepKind.POUR, 240.0, 40_000))
        val tracker = StepTracker(steps)
        tracker.update(5_000, 45.0, 0.0)
        assertEquals(1, tracker.state.index)
        tracker.update(25_000, 45.0, 0.0)
        assertEquals(20f / 35f, tracker.state.progress, 0.01f)
        assertEquals(15_000L, tracker.state.dueInMs)
    }

    /**
     * The synthetic filter replay session decoded frame by frame: dripper and server (400 g, tared away), pours from 20 s
     * to 50, 100, 150 and 255 g ending at 28, 63, 78 and 88 s. Followed with that V60, whose second and third
     * pours are due at 0:45 and 1:25, the replay begins them at 0:35 and 0:50: ahead.
     */
    @Test
    fun theFilterReplaySessionRunsAheadOfTheV60() {
        val decoder = FrameDecoder()
        val readings = File("src/test/resources/sessions/synthetic-filter-lift.jsonl").readLines().drop(1).map(::JSONObject)
            .filter { it.getString("dir") == "rx" }
            .flatMap { line ->
                decoder.feed(line.getString("hex").split(' ').map { it.toInt(16).toByte() }.toByteArray())
                    .filterIsInstance<ScaleMessage.Weight>().map { line.getLong("t_ms") to it.reading.weightGrams }
            }
        val tare = readings.first().second
        val firstWaterAt = readings.first { it.second - tare > 1.0 }.first
        val tracker = StepTracker(v60)
        val smoother = FlowSmoother()
        val paces = linkedMapOf<Int, Pace>()
        for ((at, raw) in readings.filter { it.first in firstWaterAt until firstWaterAt + 69_000 }) {
            val elapsed = at - firstWaterAt
            tracker.update(elapsed, raw - tare, smoother.add(elapsed, raw - tare))
            tracker.state.pace?.let { paces.putIfAbsent(tracker.state.index, it) }
        }
        // The last pour stops at 255 g, short of the V60's 300 g: still the current step, begun ahead of 2:00.
        assertEquals(3, tracker.state.index)
        assertEquals(mapOf(0 to Pace.ON_TIME, 1 to Pace.AHEAD, 2 to Pace.AHEAD, 3 to Pace.AHEAD), paces)
    }
}
