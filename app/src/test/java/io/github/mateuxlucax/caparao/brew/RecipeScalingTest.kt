package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.Recipe
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.StepKind
import org.junit.Assert.assertEquals
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
    fun scalesFromTheLastPourWithoutRoundingSmallTargetsTogether() {
        val small = recipe.copy(
            doseGrams = 1.0,
            targetRatio = 4.0,
            steps = listOf(
                RecipeStep(StepKind.BLOOM, 1.0, 0),
                RecipeStep(StepKind.POUR, 2.0, 1_000),
                RecipeStep(StepKind.POUR, 3.0, 2_000),
            ),
        )

        val scaled = scaleRecipeToWater(small, 1.5)

        assertEquals(listOf(0.5, 1.0, 1.5), scaled.steps.map { it.targetWeightGrams })
        assertEquals(1.5, scaled.steps.last().targetWeightGrams!!, 0.0)
    }
}
