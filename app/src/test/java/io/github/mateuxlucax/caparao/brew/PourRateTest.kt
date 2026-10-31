package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.StepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PourRateTest {
    private val bloom = RecipeStep(StepKind.BLOOM, 90.0, 0)
    private val pour = RecipeStep(StepKind.POUR, 155.0, 20_000)

    @Test
    fun spreadsTheWaterLeftOverTheTimeLeft() {
        assertEquals(4.5, PourRate.target(bloom, pour, 0, 0.0)!!, 0.001)
        // Halfway with 40 g in: 50 g over 10 s.
        assertEquals(5.0, PourRate.target(bloom, pour, 10_000, 40.0)!!, 0.001)
    }

    @Test
    fun theLastPourAndALateOneUseTheDefault() {
        assertEquals(PourRate.DEFAULT, PourRate.target(pour, null, 25_000, 100.0)!!, 0.0)
        assertEquals(PourRate.DEFAULT, PourRate.target(bloom, pour, 19_000, 60.0)!!, 0.0)
    }

    @Test
    fun clampsToWhatAKettlePours() {
        assertEquals(PourRate.RANGE.endInclusive, PourRate.target(bloom, pour, 16_000, 0.0)!!, 0.0)
    }

    @Test
    fun noTargetOnceReachedOrForAWait() {
        assertNull(PourRate.target(bloom, pour, 5_000, 95.0))
        assertNull(PourRate.target(RecipeStep(StepKind.WAIT, null, 0), pour, 0, 0.0))
    }

    @Test
    fun onPaceWithinAQuarter() {
        assertTrue(PourRate.onPace(4.0, 5.0))
        assertFalse(PourRate.onPace(3.5, 5.0))
        assertFalse(PourRate.onPace(6.5, 5.0))
    }
}
