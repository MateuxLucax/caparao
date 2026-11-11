package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.ScaleReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.StepKind

class BrewSessionTest {
    @Test
    fun freeFilterSessionReturnsItsMomentsWithTheReading() {
        val session = BrewSession(BrewMode.FILTER, 20.0, 2.0, emptyList(), null, null)
        val script = SyntheticSession().hold(400.0, 0.3).hold(0.0, 1.0).ramp(0.0, 40.0, 5.0).hold(40.0, 1.0)

        val events = script.readings.map { session.onReading(it).guideEvents }.flatten()

        assertEquals(1, events.count { it == StepEvent.FirstWater })
        assertEquals(1, events.count { it == StepEvent.InTargetBand })
    }

    @Test
    fun espressoSessionOwnsItsDetectorAndReportsItsResult() {
        val session = BrewSession(BrewMode.ESPRESSO, 18.0, 2.0, emptyList(), null, null)
        assertEquals(ScaleCommand.TARE, session.armNow(0))
        assertEquals(BrewPhase.TARING, session.phase)
        assertNull(session.result)
        session.onReading(TimedReading(1_000, ScaleReading(0.0, 0.0, 0, false)))
        session.onReading(TimedReading(1_500, ScaleReading(0.0, 0.0, 0, false)))
        assertEquals(BrewPhase.READY, session.phase)
        assertEquals(18.0, session.doseGrams, 0.0)
        assertEquals(2.0, session.targetRatio, 0.0)
    }

    @Test
    fun filterSessionOwnsPauseClockAndFlowReset() {
        val session = BrewSession(
            BrewMode.FILTER,
            20.0,
            2.0,
            listOf(RecipeStep(StepKind.POUR, 50.0, 0L)),
            null,
            null,
        )
        val tare = SyntheticSession().hold(400.0, 0.3).hold(0.0, 1.0)
        tare.readings.forEach(session::onReading)
        val pour = SyntheticSession(tare.nowMs).ramp(0.0, 250.0, 30.0)
        val guideEvents = pour.readings.flatMap { session.onReading(it).guideEvents }
        assertTrue(guideEvents.any { it is StepEvent.Reached })
        val liftAt = pour.nowMs
        val lift = SyntheticSession(pour.nowMs).ramp(250.0, 190.0, 0.3)
        val paused = lift.readings.map(session::onReading).last()
        assertEquals(BrewPhase.PAUSED, paused.phase)
        assertEquals(0, session.pauseIntervals().size)
        val returnAndHold = SyntheticSession(lift.nowMs).ramp(190.0, 250.0, 0.3).hold(250.0, 2.0)
        val resumed = returnAndHold.readings.map(session::onReading).last()
        assertEquals(BrewPhase.BREWING, resumed.phase)
        assertEquals(liftAt, session.pauseIntervals().single().first)
        assertTrue(session.pauseIntervals().single().second > liftAt)
        assertTrue(session.pauseIntervals().single().second - liftAt in 1_000L..2_000L)
    }

    @Test
    fun finishRebasesTheFilterCurveAcrossAPause() {
        val session = BrewSession(BrewMode.FILTER, 20.0, 2.0, emptyList(), null, null)
        val tare = SyntheticSession().hold(400.0, 0.3).hold(0.0, 1.0)
        tare.readings.forEach(session::onReading)
        val pour = SyntheticSession(tare.nowMs).ramp(0.0, 100.0, 12.0)
        pour.readings.forEach(session::onReading)
        val lift = SyntheticSession(pour.nowMs).ramp(100.0, 40.0, 0.3)
        lift.readings.forEach(session::onReading)
        val returnAndHold = SyntheticSession(lift.nowMs).ramp(40.0, 100.0, 0.3).hold(100.0, 2.0)
        returnAndHold.readings.forEach(session::onReading)

        val activeDurationMs = returnAndHold.nowMs - filterStartMs(session) - session.pauseIntervals().sumOf { it.second - it.first }
        session.stop(returnAndHold.nowMs)
        val summary = requireNotNull(session.finish()).summary

        assertEquals(activeDurationMs, summary.durationMs)
        assertEquals(summary.durationMs, summary.curve.last().atMs)
        assertTrue(summary.curve.zipWithNext().all { (before, after) -> after.atMs - before.atMs >= 100 })
    }

    @Test
    fun finishCutsTheFrenchPressCurveAtRemoval() {
        val session = BrewSession(
            BrewMode.FRENCH_PRESS, 20.0, 2.0,
            listOf(RecipeStep(StepKind.PRESS, null, 1_000L)), null, null,
        )
        val tare = SyntheticSession().hold(400.0, 0.3).hold(0.0, 1.0)
        tare.readings.forEach(session::onReading)
        val pour = SyntheticSession(tare.nowMs).ramp(0.0, 60.0, 8.0).hold(60.0, 4.0)
        pour.readings.forEach(session::onReading)
        val startedAtMs = requireNotNull(session.startedAtMs)
        val removalAtMs = pour.nowMs
        val removal = SyntheticSession(removalAtMs).hold(0.0, 6.0)
        val removalStates = removal.readings.map(session::onReading)
        assertTrue(removalStates.last().frenchPressRemoved)
        session.stop(removal.nowMs)

        val result = requireNotNull(session.finish())

        assertFalse(result.summary.curve.isEmpty())
        assertTrue(result.summary.curve.all { it.atMs < removalAtMs - startedAtMs })
    }

    @Test
    fun freeFilterAlertsWhenItsPauseTimesOut() {
        val session = BrewSession(BrewMode.FILTER, 20.0, 2.0, emptyList(), null, null)
        val tare = SyntheticSession().hold(400.0, 0.3).hold(0.0, 1.0)
        tare.readings.forEach(session::onReading)
        val pour = SyntheticSession(tare.nowMs).ramp(0.0, 100.0, 12.0)
        pour.readings.forEach(session::onReading)
        val lift = SyntheticSession(pour.nowMs).ramp(100.0, 0.0, 0.3)
        lift.readings.forEach(session::onReading)
        val timeout = SyntheticSession(lift.nowMs).hold(0.0, 31.0)
        val endStates = timeout.readings.map(session::onReading)

        assertEquals(BrewPhase.DONE, endStates.last().phase)
        assertEquals(1, endStates.flatMap { it.guideEvents }.count { it == StepEvent.AllDone })
    }

    @Test
    fun scaleLossLeavesPreBrewSessionsWaiting() {
        val espresso = BrewSession(BrewMode.ESPRESSO, 18.0, 2.0, emptyList(), null, null)
        espresso.onScaleLost(1_000)
        assertEquals(BrewPhase.WAITING_FOR_CUP, espresso.phase)
        assertNull(espresso.result)

        val filter = BrewSession(BrewMode.FILTER, 20.0, 2.0, emptyList(), null, null)
        val tare = SyntheticSession().hold(400.0, 0.3).hold(0.0, 1.0)
        tare.readings.forEach(filter::onReading)
        assertEquals(BrewPhase.READY, filter.phase)
        filter.onScaleLost(tare.nowMs)
        assertEquals(BrewPhase.READY, filter.phase)
        assertNull(filter.result)
    }

    @Test
    fun scaleLossKeepsFrenchPressSteepingAndInterruptsActiveFilter() {
        val press = BrewSession(
            BrewMode.FRENCH_PRESS, 20.0, 2.0,
            listOf(RecipeStep(StepKind.PRESS, null, 60_000L)), null, null,
        )
        val pressReadings = SyntheticSession().hold(400.0, 0.3).hold(0.0, 1.0).ramp(0.0, 50.0, 2.0)
        pressReadings.readings.forEach(press::onReading)
        assertEquals(BrewPhase.BREWING, press.phase)
        press.onScaleLost(pressReadings.nowMs)
        assertEquals(BrewPhase.BREWING, press.phase)
        assertNull(press.result)

        val filter = BrewSession(BrewMode.FILTER, 20.0, 2.0, emptyList(), null, null)
        val filterReadings = SyntheticSession().hold(400.0, 0.3).hold(0.0, 1.0).ramp(0.0, 5.0, 1.0)
        filterReadings.readings.forEach(filter::onReading)
        assertEquals(BrewPhase.BREWING, filter.phase)
        filter.onScaleLost(filterReadings.nowMs)
        assertEquals(BrewPhase.DONE, filter.phase)
        assertTrue(requireNotNull(filter.result).interrupted)
    }

    @Test
    fun scaleLossInterruptsAnActiveEspresso() {
        val espresso = BrewSession(BrewMode.ESPRESSO, 18.0, 2.0, emptyList(), null, null)
        assertEquals(ScaleCommand.TARE, espresso.armNow(0))
        val tare = SyntheticSession().hold(0.0, 2.0)
        tare.readings.forEach(espresso::onReading)
        val firstDrops = SyntheticSession(tare.nowMs).hold(0.0, 6.0).ramp(0.0, 4.0, 4.0)
        firstDrops.readings.forEach(espresso::onReading)
        assertEquals(BrewPhase.BREWING, espresso.phase)

        espresso.onScaleLost(firstDrops.nowMs)

        assertEquals(BrewPhase.DONE, espresso.phase)
        assertTrue(requireNotNull(espresso.result).interrupted)
    }

    private fun filterStartMs(session: BrewSession) = requireNotNull(session.startedAtMs)
}
