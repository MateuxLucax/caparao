package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.RecipeStep

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
