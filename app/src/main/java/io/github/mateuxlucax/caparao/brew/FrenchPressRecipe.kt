package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.Recipe
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.StepKind

/** Older French press recipes may not have a steep deadline; use the original four-minute default while brewing. */
fun Recipe.withDefaultFrenchPressSteep(): Recipe =
    if (mode == BrewMode.FRENCH_PRESS && steps.lastOrNull()?.kind != StepKind.PRESS) {
        copy(steps = steps + RecipeStep(StepKind.PRESS, targetWeightGrams = null, targetAtMs = DEFAULT_STEEP_MS))
    } else {
        this
    }

private const val DEFAULT_STEEP_MS = 4 * 60 * 1000L
