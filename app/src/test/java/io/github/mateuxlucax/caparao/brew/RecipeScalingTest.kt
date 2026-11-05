package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.Recipe
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.StepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeScalingTest {
    private val recipe = Recipe(
        id = "v60",
        name = "V60 One and Done",
        mode = BrewMode.FILTER,
        method = "V60",
        doseGrams = 15.0,
        targetRatio = 16.7,
        steps = listOf(
            RecipeStep(StepKind.BLOOM, 45.0, 0),
            RecipeStep(StepKind.POUR, 90.0, 30_000),
            RecipeStep(StepKind.POUR, 250.0, 60_000),
        ),
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun fourHundredMlScalesDoseAndPourTargetsButKeepsTimesAndStoredRecipe() {
        val scaled = scaleRecipeToWater(recipe, 400.0)

        assertEquals(400.0 / 16.7, scaled.doseGrams, 1e-9)
        assertEquals(16.7, scaled.targetRatio, 0.0)
        assertEquals(listOf(72.0, 144.0, 400.0), scaled.steps.map { it.targetWeightGrams })
        assertEquals(listOf(0L, 30_000L, 60_000L), scaled.steps.map { it.targetAtMs })
        assertEquals(15.0, recipe.doseGrams, 0.0)
        assertEquals(listOf(45.0, 90.0, 250.0), recipe.steps.map { it.targetWeightGrams })
        assertNotSame(recipe, scaled)
    }

    @Test
    fun guideCompletesAtTheScaledWaterTarget() {
        val tracker = StepTracker(scaleRecipeToWater(recipe, 400.0).steps)

        tracker.update(0, 0.0, 0.0)
        tracker.update(1_000, 72.0, 0.0)
        tracker.update(31_000, 72.0, 0.0)
        tracker.update(32_000, 144.0, 0.0)
        val done = tracker.update(61_000, 400.0, 0.0)

        assertTrue(done.contains(StepEvent.AllDone))
        assertEquals(3, tracker.state.index)
    }
}
