package dev.mateux.caparao.brew

import dev.mateux.caparao.model.BagRecipeAdjustment
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.Recipe
import dev.mateux.caparao.model.RecipeStep
import dev.mateux.caparao.model.StepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BagRecipeAdjustmentTest {
    private val recipe = Recipe(
        id = "v60",
        name = "V60",
        mode = BrewMode.FILTER,
        method = "V60",
        doseGrams = 15.0,
        targetRatio = 16.0,
        grind = "18",
        waterTempCelsius = 94,
        steps = listOf(
            RecipeStep(StepKind.BLOOM, 45.0, 0),
            RecipeStep(StepKind.POUR, 125.0, 30_000),
            RecipeStep(StepKind.POUR, 240.0, 60_000),
        ),
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun appliesOverridesAndRescalesCumulativePourTargetsForChangedDose() {
        val adjusted = applyBagRecipeAdjustment(
            recipe,
            BagRecipeAdjustment("bag", "v60", grind = 19, waterTempCelsius = 92, doseGrams = 18.0),
        )

        assertEquals("19", adjusted.grind)
        assertEquals(92, adjusted.waterTempCelsius)
        assertEquals(18.0, adjusted.doseGrams, 0.0)
        assertEquals(listOf(54.0, 150.0, 288.0), adjusted.steps.map { it.targetWeightGrams })
        assertEquals(listOf(0L, 30_000L, 60_000L), adjusted.steps.map { it.targetAtMs })
    }

    @Test
    fun nullOverridesKeepTheRecipeValuesAndSteps() {
        val adjusted = applyBagRecipeAdjustment(recipe, BagRecipeAdjustment("bag", "v60"))

        assertEquals(recipe, adjusted)
    }

    @Test
    fun rejectsAdjustmentForAnotherRecipe() {
        assertThrows(IllegalArgumentException::class.java) {
            applyBagRecipeAdjustment(recipe, BagRecipeAdjustment("bag", "other"))
        }
    }
}
