package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.model.Recipe
import kotlin.math.roundToInt

/** Returns a per-brew copy scaled to [waterGrams], keeping its ratio and scheduled step times. */
fun scaleRecipeToWater(recipe: Recipe, waterGrams: Double): Recipe {
    require(waterGrams > 0.0)
    require(recipe.targetRatio > 0.0)

    val factor = waterGrams / recipe.targetYieldGrams
    val lastPour = recipe.steps.indexOfLast { it.kind.pours }
    val steps = recipe.steps.mapIndexed { index, step ->
        if (!step.kind.pours || step.targetWeightGrams == null) {
            step
        } else {
            step.copy(
                targetWeightGrams = if (index == lastPour) waterGrams else (step.targetWeightGrams * factor).roundToInt().toDouble(),
            )
        }
    }
    return recipe.copy(doseGrams = waterGrams / recipe.targetRatio, steps = steps)
}
