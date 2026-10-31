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
    fun theStepsWaterOverItsTime() {
        // 90 g in the 20 s before the next step, whatever is on the scale.
        assertEquals(4.5, PourRate.target(bloom, null, pour, 0.0)!!, 0.001)
        assertEquals(4.5, PourRate.target(bloom, null, pour, 60.0)!!, 0.001)
        // 65 g from the last target, over the next 20 s.
        assertEquals(3.25, PourRate.target(pour, 90.0, RecipeStep(StepKind.POUR, 205.0, 40_000), 100.0)!!, 0.001)
    }

    @Test
    fun theLastPourUsesTheDefault() {
        assertEquals(PourRate.DEFAULT, PourRate.target(pour, 90.0, null, 100.0)!!, 0.0)
    }

    @Test
    fun clampsToWhatAKettlePours() {
        assertEquals(PourRate.RANGE.endInclusive, PourRate.target(bloom, null, RecipeStep(StepKind.POUR, 155.0, 2_000), 0.0)!!, 0.0)
    }

    @Test
    fun noTargetOnceReachedOrForAWait() {
        assertNull(PourRate.target(bloom, null, pour, 95.0))
        assertNull(PourRate.target(RecipeStep(StepKind.WAIT, null, 0), null, pour, 0.0))
    }

    @Test
    fun onPaceWithinAQuarter() {
        assertTrue(PourRate.onPace(4.0, 5.0))
        assertFalse(PourRate.onPace(3.5, 5.0))
        assertFalse(PourRate.onPace(6.5, 5.0))
    }
}
