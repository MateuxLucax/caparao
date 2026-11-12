package dev.mateux.caparao.brew

import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.RecipeStep

internal fun createBrewSession(
    plan: BrewPlan,
    filterConfig: FilterConfig,
): BrewSession = if (plan.mode == BrewMode.FRENCH_PRESS) {
    FrenchPressSession(
        plan.doseGrams,
        plan.targetRatio,
        plan.brewRecipe?.steps.orEmpty(),
        plan.brewRecipe?.targetMinMs,
        plan.brewRecipe?.targetMaxMs,
        freeWaterGrams = if (plan.recipe == null) plan.doseGrams * plan.targetRatio else null,
        freeSteepMs = if (plan.recipe == null) plan.steepMs else null,
        filterConfig = filterConfig,
    )
} else {
    DetectorBrewSession(
        plan.mode,
        plan.doseGrams,
        plan.targetRatio,
        plan.brewRecipe?.steps.orEmpty(),
        plan.brewRecipe?.targetMinMs,
        plan.brewRecipe?.targetMaxMs,
        filterConfig = filterConfig,
    )
}

internal fun createBrewSession(
    mode: BrewMode,
    doseGrams: Double,
    targetRatio: Double,
    steps: List<RecipeStep>,
    targetMinMs: Long?,
    targetMaxMs: Long?,
    freeWaterGrams: Double?,
    freeSteepMs: Long?,
    filterConfig: FilterConfig,
): BrewSession = if (mode == BrewMode.FRENCH_PRESS) {
    FrenchPressSession(doseGrams, targetRatio, steps, targetMinMs, targetMaxMs, freeWaterGrams, freeSteepMs, filterConfig)
} else {
    DetectorBrewSession(mode, doseGrams, targetRatio, steps, targetMinMs, targetMaxMs, filterConfig = filterConfig)
}
