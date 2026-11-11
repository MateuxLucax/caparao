package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.ScaleReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.StepKind

class BrewSessionTest {
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
        val script = SyntheticSession()
        script.hold(400.0, 0.3).hold(0.0, 1.0)
        script.readings.toList().forEach { session.onFilterReading(it, isLive = true) }
        script.readings.clear()
        script.ramp(0.0, 250.0, 30.0)
        val guideEvents = script.readings.toList().flatMap { session.onFilterReading(it, isLive = true).guideEvents }
        script.readings.clear()
        assertTrue(guideEvents.any { it is StepEvent.Reached })
        val liftAt = script.nowMs
        script.ramp(250.0, 190.0, 0.3)
        val paused = script.readings.map { session.onFilterReading(it, isLive = true) }.last()
        script.readings.clear()
        assertEquals(BrewPhase.PAUSED, paused.phase)
        assertEquals(0L, session.pauseIntervals().size.toLong())
        script.ramp(190.0, 250.0, 0.3).hold(250.0, 2.0)
        val resumed = script.readings.map { session.onFilterReading(it, isLive = true) }.last()
        assertEquals(BrewPhase.BREWING, resumed.phase)
        assertEquals(liftAt, session.pauseIntervals().single().first)
        assertTrue(session.pauseIntervals().single().second > liftAt)
        assertTrue(session.pauseIntervals().single().second - liftAt in 1_000L..2_000L)
    }
}
