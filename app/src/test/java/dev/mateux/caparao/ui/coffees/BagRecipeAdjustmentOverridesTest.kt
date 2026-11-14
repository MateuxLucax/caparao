package dev.mateux.caparao.ui.coffees

import dev.mateux.caparao.model.BagRecipeAdjustment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BagRecipeAdjustmentOverridesTest {
    @Test
    fun aValueOverrideCounts() {
        assertTrue(BagRecipeAdjustment("bag", "recipe", grind = 9).overrides)
    }

    @Test
    fun tunedAloneCountsSoTheDetailKeepsItsMark() {
        // #468 saves exactly this for an on-target balanced brew: tuned, with no value overrides.
        assertTrue(BagRecipeAdjustment("bag", "recipe", tuned = true).overrides)
    }

    @Test
    fun anEmptyAdjustmentDoesNot() {
        assertFalse(BagRecipeAdjustment("bag", "recipe").overrides)
    }
}
