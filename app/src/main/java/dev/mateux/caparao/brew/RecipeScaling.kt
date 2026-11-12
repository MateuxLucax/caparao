package dev.mateux.caparao.brew

import dev.mateux.caparao.model.Recipe

/** Returns a per-brew copy scaled to [waterGrams], keeping its ratio and scheduled step times. */
fun scaleRecipeToWater(recipe: Recipe, waterGrams: Double): Recipe {
    require(waterGrams > 0.0)
    require(recipe.targetRatio > 0.0)

    val lastPour = recipe.steps.indexOfLast { it.kind.pours && it.targetWeightGrams != null }
    val baseWater = recipe.steps.getOrNull(lastPour)?.targetWeightGrams ?: recipe.targetYieldGrams
    val factor = waterGrams / baseWater
    val steps = recipe.steps.mapIndexed { index, step ->
        if (!step.kind.pours || step.targetWeightGrams == null) {
            step
        } else {
            step.copy(
                targetWeightGrams = if (index == lastPour) waterGrams else step.targetWeightGrams * factor,
            )
        }
    }
    return recipe.copy(doseGrams = waterGrams / recipe.targetRatio, steps = steps)
}
