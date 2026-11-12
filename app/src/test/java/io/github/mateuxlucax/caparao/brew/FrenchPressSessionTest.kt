package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.StepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrenchPressSessionTest {
    @Test
    fun freePressOwnsItsGuideAndSteepClockWithoutFilterPauseEvents() {
        val session = BrewSession(
            BrewMode.FRENCH_PRESS,
            doseGrams = 20.0,
            targetRatio = 2.0,
            steps = emptyList(),
            targetMinMs = null,
            targetMaxMs = null,
            freeWaterGrams = 80.0,
            freeSteepMs = 500L,
        )
        assertTrue(session is FrenchPressSession)
        assertEquals(listOf(StepKind.POUR, StepKind.PRESS), session.guideSteps.map { it.kind })

        val readings = SyntheticSession()
            .hold(400.0, 0.3)
            .hold(0.0, 1.0)
            .ramp(0.0, 80.0, 2.0)
            .hold(80.0, 4.0)
        val events = readings.readings.flatMap { session.onReading(it).guideEvents }

        assertEquals(BrewPhase.BREWING, session.phase)
        assertFalse(events.any { it == StepEvent.Paused || it == StepEvent.Resumed })
        assertTrue(events.contains(StepEvent.FrenchPressDeadline))
        session.onScaleLost(session.startedAtMs!!)
        assertEquals(BrewPhase.BREWING, session.phase)
        assertTrue(session.onClock(readings.nowMs + 15_000L).guideEvents.contains(StepEvent.FrenchPressReminder))
    }
}
