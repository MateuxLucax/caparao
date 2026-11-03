package io.github.mateuxlucax.caparao.ui.brew

import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.StepKind

/** The guide line follows the French press's timed brew; other recipes retain their step-based progress. */
internal fun guideProgress(mode: BrewMode, steps: List<RecipeStep>, currentIndex: Int, elapsedMs: Long): Float {
    val pressTargetMs = if (mode == BrewMode.FRENCH_PRESS) {
        steps.lastOrNull()?.takeIf { it.kind == StepKind.PRESS }?.targetAtMs
    } else {
        null
    }
    if (pressTargetMs != null && pressTargetMs > 0L) {
        return (elapsedMs.toDouble() / pressTargetMs).toFloat().coerceIn(0f, 1f)
    }

    if (steps.isEmpty()) return 0f
    return ((currentIndex + 1f) / steps.size).coerceIn(0f, 1f)
}
