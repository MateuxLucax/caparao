package dev.mateux.caparao.brew

import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.ScaleReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.mateux.caparao.model.RecipeStep
import dev.mateux.caparao.model.StepKind

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
    fun aFreeFilterEmitsWaterBandPauseResumeAndEndEventsOnce() {
        val session = BrewSession(BrewMode.FILTER, 15.0, 16.0, emptyList(), null, null)
        val events = mutableListOf<StepEvent>()
        fun feed(script: SyntheticSession) { events += script.readings.flatMap { session.onReading(it).guideEvents } }

        val tare = SyntheticSession().hold(320.0, 0.2).hold(0.0, 1.0)
        feed(tare)
        val low = SyntheticSession(tare.nowMs).ramp(0.0, 2.0, 0.4).ramp(2.0, 200.0, 9.0).hold(200.0, 1.1)
        feed(low)
        val lift = SyntheticSession(low.nowMs).ramp(200.0, 0.0, 0.5)
        feed(lift)
        val returned = SyntheticSession(lift.nowMs).hold(0.0, 0.2).ramp(0.0, 200.0, 0.5).hold(200.0, 2.1)
        feed(returned)
        val atBand = SyntheticSession(returned.nowMs).ramp(200.0, 230.0, 3.0).hold(230.0, 1.1)
        feed(atBand)
        val pastBand = SyntheticSession(atBand.nowMs).ramp(230.0, 260.0, 2.0).hold(260.0, 1.1)
        feed(pastBand)
        events += session.stop(pastBand.nowMs)

        assertEquals(BrewPhase.DONE, session.phase)
        assertEquals(1, events.count { it == StepEvent.FirstWater })
        assertEquals(1, events.count { it == StepEvent.InTargetBand })
        assertEquals(1, events.count { it == StepEvent.PastTargetBand })
        assertEquals(1, events.count { it == StepEvent.Paused })
        assertEquals(1, events.count { it == StepEvent.Resumed })
        assertEquals(1, events.count { it == StepEvent.AllDone })
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

    @Test
    fun aFilterLiftOfAnyLengthAfterWaterPausesAndNeverReturnsToPreparation() {
        val session = BrewSession(BrewMode.FILTER, 15.0, 16.0, emptyList(), null, null)
        val tare = SyntheticSession().hold(320.0, 0.2).hold(0.0, 1.0)
        tare.readings.forEach(session::onReading)
        val pour = SyntheticSession(tare.nowMs).ramp(0.0, 50.0, 4.0).hold(50.0, 1.1)
        pour.readings.forEach(session::onReading)
        val lift = SyntheticSession(pour.nowMs).ramp(50.0, -90.0, 0.3).hold(-90.0, 10.0)
        val states = lift.readings.map(session::onReading)

        assertEquals(BrewPhase.PAUSED, states.last().phase)
        assertFalse(states.any { it.returnedToPreparation })
    }

    @Test
    fun aFreeFilterLiftEndsTheBrewOnlyOnceTheWaterIsInTheRatioBand() {
        val session = BrewSession(BrewMode.FILTER, 15.0, 16.0, emptyList(), null, null)
        val tare = SyntheticSession().hold(320.0, 0.2).hold(0.0, 1.0)
        tare.readings.forEach(session::onReading)
        val belowBand = SyntheticSession(tare.nowMs).ramp(0.0, 200.0, 10.0).hold(200.0, 1.1)
        belowBand.readings.forEach(session::onReading)
        val firstLift = SyntheticSession(belowBand.nowMs).ramp(200.0, 0.0, 0.5)
        val paused = firstLift.readings.map(session::onReading).last()
        assertEquals(BrewPhase.PAUSED, paused.phase)
        assertFalse(paused.filterLiftEndsBrew)

        val returnAndSettle = SyntheticSession(firstLift.nowMs).ramp(0.0, 200.0, 0.5).hold(200.0, 2.1)
        returnAndSettle.readings.forEach(session::onReading)
        assertEquals(BrewPhase.BREWING, session.phase)
        val target = SyntheticSession(returnAndSettle.nowMs).ramp(200.0, 240.0, 3.0).hold(240.0, 1.2)
        target.readings.forEach(session::onReading)
        val finalLift = SyntheticSession(target.nowMs).ramp(240.0, 0.0, 0.1)
        val ended = finalLift.readings.map(session::onReading).last()

        assertTrue(ended.filterLiftEndsBrew)
        session.stop(finalLift.nowMs)
        assertEquals(BrewPhase.DONE, session.phase)
        assertEquals(240.0, requireNotNull(session.result).yieldGrams, 0.3)

        val pastBand = BrewSession(BrewMode.FILTER, 15.0, 16.0, emptyList(), null, null)
        val pastTare = SyntheticSession().hold(320.0, 0.2).hold(0.0, 1.0)
        pastTare.readings.forEach(pastBand::onReading)
        val pastPour = SyntheticSession(pastTare.nowMs).ramp(0.0, 260.0, 10.0).hold(260.0, 1.2)
        pastPour.readings.forEach(pastBand::onReading)
        val pastLift = SyntheticSession(pastPour.nowMs).ramp(260.0, 0.0, 0.1)
        val pastEnd = pastLift.readings.map(pastBand::onReading).last()
        assertTrue(pastEnd.filterLiftEndsBrew)
        pastBand.stop(pastLift.nowMs)
        assertEquals(BrewPhase.DONE, pastBand.phase)
        assertEquals(260.0, requireNotNull(pastBand.result).yieldGrams, 0.3)
    }

    @Test
    fun aGuidedFilterLiftEndsTheBrewOnlyAfterTheLastPourTarget() {
        val steps = listOf(RecipeStep(StepKind.BLOOM, 40.0, 0L), RecipeStep(StepKind.POUR, 100.0, 20_000L))
        val session = BrewSession(BrewMode.FILTER, 15.0, 16.0, steps, null, null)
        val tare = SyntheticSession().hold(320.0, 0.2).hold(0.0, 1.0)
        tare.readings.forEach(session::onReading)
        val firstPour = SyntheticSession(tare.nowMs).ramp(0.0, 40.0, 8.0).hold(40.0, 12.0)
        firstPour.readings.forEach(session::onReading)
        val shortSecondPour = SyntheticSession(firstPour.nowMs).ramp(40.0, 60.0, 5.0)
        shortSecondPour.readings.forEach(session::onReading)
        val earlyLift = SyntheticSession(shortSecondPour.nowMs).ramp(60.0, 0.0, 0.5)
        val paused = earlyLift.readings.map(session::onReading).last()

        assertEquals(BrewPhase.PAUSED, paused.phase)
        assertFalse(paused.filterLiftEndsBrew)
        assertEquals(1, requireNotNull(paused.guide).index)

        val sessionAtTarget = BrewSession(BrewMode.FILTER, 15.0, 16.0, steps, null, null)
        tare.readings.forEach(sessionAtTarget::onReading)
        firstPour.readings.forEach(sessionAtTarget::onReading)
        val finalPour = SyntheticSession(firstPour.nowMs).ramp(40.0, 100.0, 12.0)
        finalPour.readings.forEach(sessionAtTarget::onReading)
        val targetLift = SyntheticSession(finalPour.nowMs).ramp(100.0, 0.0, 0.1)
        val ended = targetLift.readings.map(sessionAtTarget::onReading).last()

        assertTrue(ended.filterLiftEndsBrew)
        sessionAtTarget.stop(targetLift.nowMs)
        assertEquals(BrewPhase.DONE, sessionAtTarget.phase)
    }

    @Test
    fun aFreePressPoursUnguidedThenSteepsOnReachingTheTargetWater() {
        val session = BrewSession(
            BrewMode.FRENCH_PRESS, 20.0, 16.0, emptyList(), null, null,
            freeWaterGrams = 300.0,
            freeSteepMs = 240_000L,
        )
        val tare = SyntheticSession().hold(320.0, 0.2).hold(0.0, 1.0)
        tare.readings.forEach(session::onReading)
        val partialPour = SyntheticSession(tare.nowMs).ramp(0.0, 200.0, 15.0)
        partialPour.readings.forEach(session::onReading)
        assertEquals(0, requireNotNull(session.onClock(partialPour.nowMs).guide).index)
        val targetPour = SyntheticSession(partialPour.nowMs).ramp(200.0, 300.0, 8.0)
        val targetStates = targetPour.readings.map(session::onReading)
        val startedAt = requireNotNull(session.startedAtMs)
        assertTrue(targetStates.flatMap { it.guideEvents }.contains(StepEvent.Reached(0)))
        assertEquals(1, requireNotNull(session.onClock(targetPour.nowMs).guide).index)
        assertTrue(session.onClock(startedAt + 239_000).guideEvents.none { it == StepEvent.FrenchPressDeadline })
        assertEquals(listOf(StepEvent.FrenchPressDeadline), session.onClock(startedAt + 240_000).guideEvents)
    }

    @Test
    fun aFrenchPressStartsOnFirstWaterAndWaitsForDoneAfterTheCarafeIsLifted() {
        val session = BrewSession(
            BrewMode.FRENCH_PRESS, 20.0, 16.0,
            listOf(RecipeStep(StepKind.PRESS, null, 60_000L)), null, null,
        )
        val tare = SyntheticSession().hold(320.0, 0.2).hold(0.0, 1.0)
        tare.readings.forEach(session::onReading)
        val water = SyntheticSession(tare.nowMs).ramp(0.0, 300.0, 30.0)
        water.readings.forEach(session::onReading)
        val lift = SyntheticSession(water.nowMs).ramp(300.0, 0.0, 0.4).hold(0.0, 10.0)
        lift.readings.forEach(session::onReading)

        assertEquals(BrewPhase.BREWING, session.phase)
        assertNull(session.result)
        session.stop(lift.nowMs)
        val result = requireNotNull(session.finish())
        assertEquals(300.0, result.result.yieldGrams, 0.5)
        assertEquals(300.0, result.summary.yieldGrams, 0.5)
    }

    private fun filterStartMs(session: BrewSession) = requireNotNull(session.startedAtMs)
}
