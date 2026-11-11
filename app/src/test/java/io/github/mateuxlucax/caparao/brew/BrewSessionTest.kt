package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.ScaleReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
