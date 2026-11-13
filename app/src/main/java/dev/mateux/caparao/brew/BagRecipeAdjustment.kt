package dev.mateux.caparao.brew

import dev.mateux.caparao.model.BagRecipeAdjustment
import dev.mateux.caparao.model.Recipe

/** Applies a coffee's overrides to a recipe; a changed dose rescales its cumulative pour targets. */
fun applyBagRecipeAdjustment(recipe: Recipe, adjustment: BagRecipeAdjustment): Recipe {
    require(adjustment.recipeId == recipe.id) { "Adjustment belongs to a different recipe" }

    val dose = adjustment.doseGrams
    val adjustedRecipe = if (dose == null) {
        recipe
    } else {
        require(dose > 0.0)
        scaleRecipeToWater(recipe, dose * recipe.targetRatio).copy(doseGrams = dose)
    }
    return adjustedRecipe.copy(
        grind = adjustment.grind?.toString() ?: recipe.grind,
        waterTempCelsius = adjustment.waterTempCelsius ?: recipe.waterTempCelsius,
    )
}
